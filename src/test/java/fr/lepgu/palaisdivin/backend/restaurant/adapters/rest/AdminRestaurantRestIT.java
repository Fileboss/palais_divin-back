package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.review.adapters.rest.CreateReviewRequest;
import fr.lepgu.palaisdivin.backend.review.adapters.rest.ReviewResponse;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class AdminRestaurantRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired JdbcClient jdbcClient;
  @Autowired UserRepositoryPort users;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
  }

  @Test
  void deleteExistingRestaurantReturns204_andSubsequentGetReturns404() {
    UUID restaurantId = seedRestaurant();

    ResponseEntity<Void> resp =
        adminClient()
            .delete()
            .uri("/api/v1/admin/restaurants/{id}", restaurantId)
            .retrieve()
            .toBodilessEntity();

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<String> getResp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/public/restaurants/{id}", restaurantId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);
    assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteUnknownIdReturns404ProblemDetail() {
    UUID missing = UUID.randomUUID();

    ResponseEntity<String> resp =
        adminClient()
            .delete()
            .uri("/api/v1/admin/restaurants/{id}", missing)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/not-found");
    assertThat(resp.getBody()).contains(missing.toString());
  }

  @Test
  void anonymousDeleteReturns401() {
    UUID restaurantId = seedRestaurant();

    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .delete()
            .uri("/api/v1/admin/restaurants/{id}", restaurantId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void userRoleDeleteReturns403() {
    UUID restaurantId = seedRestaurant();
    String userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");

    ResponseEntity<String> resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build()
            .delete()
            .uri("/api/v1/admin/restaurants/{id}", restaurantId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resp.getBody()).contains("/problems/forbidden");
  }

  @Test
  void deleteCascadesReviewsAtPostgresLevel() {
    String userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");
    String subject = TestKeycloakTokens.subjectOf(userToken);
    users
        .findBySubject(subject)
        .orElseGet(
            () ->
                users.save(
                    new User(
                        UserId.newId(),
                        subject,
                        "testuser@example.test",
                        "Test User",
                        Instant.now())));

    RestClient userClient =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build();

    RestaurantResponse seeded =
        userClient
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);
    UUID restaurantId = seeded.id();

    ReviewResponse review =
        userClient
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(5, "Stellar"))
            .retrieve()
            .body(ReviewResponse.class);

    Long preCount =
        jdbcClient
            .sql("SELECT count(*) FROM review WHERE id = :id")
            .param("id", review.id())
            .query(Long.class)
            .single();
    assertThat(preCount).isEqualTo(1L);

    ResponseEntity<Void> delete =
        adminClient()
            .delete()
            .uri("/api/v1/admin/restaurants/{id}", restaurantId)
            .retrieve()
            .toBodilessEntity();
    assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    Long postReviewCount =
        jdbcClient
            .sql("SELECT count(*) FROM review WHERE id = :id")
            .param("id", review.id())
            .query(Long.class)
            .single();
    assertThat(postReviewCount).isZero();

    Long postRestaurantCount =
        jdbcClient
            .sql("SELECT count(*) FROM restaurant WHERE id = :id")
            .param("id", restaurantId)
            .query(Long.class)
            .single();
    assertThat(postRestaurantCount).isZero();
  }

  private UUID seedRestaurant() {
    String userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");
    RestaurantResponse seeded =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build()
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);
    return seeded.id();
  }

  private RestClient adminClient() {
    String adminToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testadmin", "testadmin");
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .build();
  }
}
