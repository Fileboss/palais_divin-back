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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class ReviewRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired UserRepositoryPort users;
  @Autowired JdbcClient jdbcClient;

  private String userToken;
  private UUID restaurantId;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();

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

    RestaurantResponse seeded =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);
    restaurantId = seeded.id();
  }

  @Test
  void postReturns201WithLocationAndBody() {
    ResponseEntity<ReviewResponse> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(4, "Excellent"))
            .retrieve()
            .toEntity(ReviewResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getHeaders().getLocation()).isNotNull();
    ReviewResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.restaurantId()).isEqualTo(restaurantId);
    assertThat(body.rating()).isEqualTo(4);
    assertThat(body.comment()).isEqualTo("Excellent");
    assertThat(resp.getHeaders().getLocation().toString())
        .endsWith("/api/v1/user/restaurants/" + restaurantId + "/reviews/" + body.id());
  }

  @Test
  void duplicateReviewReturns409() {
    authedClient()
        .post()
        .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateReviewRequest(4, "First"))
        .retrieve()
        .toEntity(ReviewResponse.class);

    ResponseEntity<String> dup =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(2, "Second"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(dup.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    assertThat(dup.getBody()).contains("/problems/conflict");
  }

  @Test
  void idempotencyKeyReplays201() {
    String key = "key-" + UUID.randomUUID();
    ReviewResponse first =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(4, "Original"))
            .retrieve()
            .body(ReviewResponse.class);

    ResponseEntity<ReviewResponse> replay =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(4, "Original"))
            .retrieve()
            .toEntity(ReviewResponse.class);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(replay.getBody().id()).isEqualTo(first.id());
  }

  @Test
  void idempotencyKeyReplaysOriginalBodyEvenIfRequestBodyDiffers() {
    String key = "key-" + UUID.randomUUID();
    ReviewResponse first =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(4, "Original"))
            .retrieve()
            .body(ReviewResponse.class);

    ReviewResponse replay =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(2, "Different"))
            .retrieve()
            .body(ReviewResponse.class);

    assertThat(replay.id()).isEqualTo(first.id());
    assertThat(replay.rating()).isEqualTo(4);
    assertThat(replay.comment()).isEqualTo("Original");
  }

  @Test
  void missingRestaurantReturns404() {
    UUID missing = UUID.randomUUID();
    ResponseEntity<String> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", missing)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(4, null))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
    assertThat(resp.getBody()).contains(missing.toString());
  }

  @Test
  void anonymousReturns401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(4, null))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
