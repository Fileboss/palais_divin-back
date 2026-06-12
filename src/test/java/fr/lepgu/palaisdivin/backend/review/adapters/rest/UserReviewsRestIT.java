package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CreateRestaurantRequest;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantResponse;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class UserReviewsRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired UserRepositoryPort users;
  @Autowired JdbcClient jdbcClient;
  @Autowired Neo4jClient neo4jClient;

  private String userToken;
  private UUID r1;
  private UUID r2;
  private UUID r3;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();

    userToken =
        TestKeycloakTokens.passwordGrant(keycloak, REALM, FRONTEND_CLIENT, USERNAME, PASSWORD);
    String subject = TestKeycloakTokens.subjectOf(userToken);

    users
        .findBySubject(subject)
        .orElseGet(
            () ->
                users.save(
                    new User(
                        UserId.newId(),
                        subject,
                        USERNAME + "@example.test",
                        "Test User",
                        Instant.now())));

    r1 = createRestaurant("Septime", "80 Rue de Charonne");
    r2 = createRestaurant("Frenchie", "5 Rue du Nil");
    r3 = createRestaurant("Clamato", "80 Rue de Charonne");

    postReview(r1, 4, "Excellent");
    postReview(r3, 5, "Stellar");
  }

  @Test
  void returnsReviewedAndNullEntriesPreservingRequestOrder() {
    String body =
        authedClient()
            .get()
            .uri("/api/v1/user/reviews?restaurantIds={a},{b},{c}", r2, r1, r3)
            .retrieve()
            .body(String.class);

    assertThat(body).isNotNull();
    // Order preserved: r2 (null) first, then r1, then r3.
    int posR2 = body.indexOf(r2.toString());
    int posR1 = body.indexOf(r1.toString());
    int posR3 = body.indexOf(r3.toString());
    assertThat(posR2).isLessThan(posR1);
    assertThat(posR1).isLessThan(posR3);
    // r2 explicitly null (not stripped).
    assertThat(body).contains("\"" + r2 + "\":null");
    // r1 + r3 carry full ReviewResponse fields.
    assertThat(body).contains("\"rating\":4");
    assertThat(body).contains("\"rating\":5");
    assertThat(body).contains("\"comment\":\"Excellent\"");
    assertThat(body).contains("\"comment\":\"Stellar\"");
  }

  @Test
  void missingRestaurantIdsParamReturns400() {
    ResponseEntity<String> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/reviews")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void emptyRestaurantIdsParamReturns400() {
    ResponseEntity<String> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/reviews?restaurantIds=")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anonymousReturns401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/user/reviews?restaurantIds={a}", r1)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  private UUID createRestaurant(String name, String address) {
    RestaurantResponse seeded =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest(name, address))
            .retrieve()
            .body(RestaurantResponse.class);
    return seeded.id();
  }

  private void postReview(UUID restaurantId, int rating, String comment) {
    authedClient()
        .post()
        .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateReviewRequest(rating, comment))
        .retrieve()
        .toBodilessEntity();
  }

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
