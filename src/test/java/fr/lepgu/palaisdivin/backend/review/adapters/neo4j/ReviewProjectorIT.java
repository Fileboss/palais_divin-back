package fr.lepgu.palaisdivin.backend.review.adapters.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CreateRestaurantRequest;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantResponse;
import fr.lepgu.palaisdivin.backend.review.adapters.rest.CreateReviewRequest;
import fr.lepgu.palaisdivin.backend.review.adapters.rest.ReviewResponse;
import fr.lepgu.palaisdivin.backend.review.domain.events.ReviewCreated;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.OutboxWorker;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class ReviewProjectorIT extends AbstractIntegrationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @LocalServerPort int port;

  @Autowired KeycloakContainer keycloak;
  @Autowired Neo4jClient neo4jClient;
  @Autowired JdbcClient jdbcClient;
  @Autowired UserRepositoryPort users;
  @Autowired ReviewProjector projector;
  @Autowired OutboxWorker worker;
  @Autowired PlatformTransactionManager txManager;
  @Autowired BanApiClientStub banApiClient;

  private String userToken;
  private UUID authorId;

  @BeforeEach
  void setUp() {
    banApiClient.reset();

    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    jdbcClient.sql("DELETE FROM user_connection").update();
    jdbcClient.sql("DELETE FROM app_user").update();
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();

    userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, "palaisdivin", "palais-divin-frontend", "testuser", "testpassword");
    String subject = TestKeycloakTokens.subjectOf(userToken);

    User author =
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
    authorId = author.id().value();
  }

  @Test
  void postingAReviewProjectsRatedEdgeAfterWorkerDrain() {
    RestClient client =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build();

    RestaurantResponse seededRestaurant =
        client
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);
    UUID restaurantId = seededRestaurant.id();

    new TransactionTemplate(txManager).executeWithoutResult(s -> worker.drainBatch());

    ReviewResponse review =
        client
            .post()
            .uri("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReviewRequest(5, "Exceptional"))
            .retrieve()
            .body(ReviewResponse.class);

    new TransactionTemplate(txManager).executeWithoutResult(s -> worker.drainBatch());

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              Map<String, Object> edge =
                  neo4jClient
                      .query(
                          "MATCH (u:User {id: $authorId})-[r:RATED]->(rest:Restaurant {id:"
                              + " $restaurantId}) RETURN r.score AS score, r.reviewId AS reviewId")
                      .bindAll(
                          Map.of(
                              "authorId",
                              authorId.toString(),
                              "restaurantId",
                              restaurantId.toString()))
                      .fetch()
                      .one()
                      .orElse(null);
              assertThat(edge).isNotNull();
              assertThat(((Number) edge.get("score")).intValue()).isEqualTo(5);
              assertThat(edge.get("reviewId")).isEqualTo(review.id().toString());
            });

    Long pending =
        jdbcClient
            .sql("SELECT count(*) FROM outbox_event WHERE status = 'PENDING'")
            .query(Long.class)
            .single();
    assertThat(pending).isZero();
  }

  @Test
  void projectingTheSameEventTwiceLeavesExactlyOneRatedEdge() {
    UUID reviewId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    UUID someAuthor = UUID.randomUUID();
    JsonNode payload =
        MAPPER.valueToTree(
            new ReviewCreated(
                reviewId,
                restaurantId,
                someAuthor,
                4,
                "Solid",
                Instant.parse("2026-05-28T10:00:00Z")));

    projector.project("ReviewCreated", payload);
    projector.project("ReviewCreated", payload);

    Long count =
        neo4jClient
            .query("MATCH ()-[r:RATED]->() WHERE r.reviewId = $id RETURN count(r) AS c")
            .bindAll(Map.of("id", reviewId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void projectingReviewBeforeUserAndRestaurantCreatesStubNodesAndEdge() {
    UUID reviewId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    UUID someAuthor = UUID.randomUUID();
    JsonNode payload =
        MAPPER.valueToTree(
            new ReviewCreated(
                reviewId,
                restaurantId,
                someAuthor,
                3,
                null,
                Instant.parse("2026-05-28T10:00:00Z")));

    projector.project("ReviewCreated", payload);

    Map<String, Object> result =
        neo4jClient
            .query(
                "MATCH (u:User {id: $authorId})-[r:RATED]->(rest:Restaurant {id: $restaurantId}) "
                    + "RETURN r.score AS score, r.reviewId AS reviewId, "
                    + "size(keys(u)) AS userKeys, size(keys(rest)) AS restKeys")
            .bindAll(
                Map.of("authorId", someAuthor.toString(), "restaurantId", restaurantId.toString()))
            .fetch()
            .one()
            .orElse(null);
    assertThat(result).isNotNull();
    assertThat(((Number) result.get("score")).intValue()).isEqualTo(3);
    assertThat(result.get("reviewId")).isEqualTo(reviewId.toString());
    assertThat(((Number) result.get("userKeys")).intValue()).isEqualTo(1);
    assertThat(((Number) result.get("restKeys")).intValue()).isEqualTo(1);
  }

  @Test
  void reprojectingWithUpdatedScoreLeavesOneEdgeWithLatestScore() {
    UUID reviewId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    UUID someAuthor = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-05-28T10:00:00Z");

    projector.project(
        "ReviewCreated",
        MAPPER.valueToTree(
            new ReviewCreated(reviewId, restaurantId, someAuthor, 3, "Meh", createdAt)));
    projector.project(
        "ReviewCreated",
        MAPPER.valueToTree(
            new ReviewCreated(reviewId, restaurantId, someAuthor, 5, "Better", createdAt)));

    Map<String, Object> edge =
        neo4jClient
            .query(
                "MATCH (u:User {id: $authorId})-[r:RATED]->(rest:Restaurant {id: $restaurantId}) "
                    + "RETURN r.score AS score, count(r) AS edgeCount")
            .bindAll(
                Map.of("authorId", someAuthor.toString(), "restaurantId", restaurantId.toString()))
            .fetch()
            .one()
            .orElseThrow();
    assertThat(((Number) edge.get("edgeCount")).longValue()).isEqualTo(1L);
    assertThat(((Number) edge.get("score")).intValue()).isEqualTo(5);
  }
}
