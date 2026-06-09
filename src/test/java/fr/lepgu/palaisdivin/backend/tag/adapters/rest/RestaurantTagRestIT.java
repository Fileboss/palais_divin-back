package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CreateRestaurantRequest;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class RestaurantTagRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired JdbcClient jdbcClient;
  @Autowired Neo4jClient neo4jClient;
  @Autowired OutboxWorker worker;
  @Autowired PlatformTransactionManager txManager;
  @Autowired BanApiClientStub banApiClient;
  @Autowired UserRepositoryPort users;

  private UUID restaurantId;
  private UUID tagId;
  private String userToken;
  private String adminToken;

  @BeforeEach
  void setUp() {
    banApiClient.reset();
    jdbcClient.sql("DELETE FROM restaurant_tag").update();
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    jdbcClient.sql("DELETE FROM tag").update();
    jdbcClient.sql("DELETE FROM app_user").update();
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();

    userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");
    adminToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testadmin", "testadmin");

    String userSubject = TestKeycloakTokens.subjectOf(userToken);
    if (users.findBySubject(userSubject).isEmpty()) {
      users.save(
          new User(
              UserId.newId(), userSubject, "testuser@example.test", "Test User", Instant.now()));
    }

    restaurantId =
        userClient()
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();

    tagId =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagPayload("SPECIALTY", "natural-wine", "Natural wine"))
            .retrieve()
            .body(TagResponse.class)
            .id();
  }

  @Test
  void attach_returns_201_with_location_and_persists_row() {
    ResponseEntity<RestaurantTagResponse> resp =
        userClient()
            .post()
            .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
            .retrieve()
            .toEntity(RestaurantTagResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getHeaders().getLocation()).isNotNull();
    assertThat(resp.getHeaders().getLocation().toString())
        .endsWith("/api/v1/user/restaurants/" + restaurantId + "/tags/" + tagId);
    RestaurantTagResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.restaurantId()).isEqualTo(restaurantId);
    assertThat(body.tagId()).isEqualTo(tagId);

    Long count =
        jdbcClient
            .sql("SELECT count(*) FROM restaurant_tag WHERE restaurant_id = :r AND tag_id = :t")
            .param("r", restaurantId)
            .param("t", tagId)
            .query(Long.class)
            .single();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void attach_duplicate_returns_200_no_second_row() {
    userClient()
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
        .retrieve()
        .toBodilessEntity();

    ResponseEntity<RestaurantTagResponse> dup =
        userClient()
            .post()
            .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
            .retrieve()
            .toEntity(RestaurantTagResponse.class);

    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long count =
        jdbcClient
            .sql("SELECT count(*) FROM restaurant_tag WHERE restaurant_id = :r AND tag_id = :t")
            .param("r", restaurantId)
            .param("t", tagId)
            .query(Long.class)
            .single();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void detach_returns_204_idempotent_when_missing() {
    userClient()
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
        .retrieve()
        .toBodilessEntity();

    ResponseEntity<Void> first =
        userClient()
            .delete()
            .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
            .retrieve()
            .toBodilessEntity();
    ResponseEntity<Void> second =
        userClient()
            .delete()
            .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
            .retrieve()
            .toBodilessEntity();

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    Long count =
        jdbcClient
            .sql("SELECT count(*) FROM restaurant_tag WHERE restaurant_id = :r AND tag_id = :t")
            .param("r", restaurantId)
            .param("t", tagId)
            .query(Long.class)
            .single();
    assertThat(count).isZero();
  }

  @Test
  void attach_unknown_tag_returns_404() {
    UUID missingTag = UUID.randomUUID();
    ResponseEntity<String> resp =
        userClient()
            .post()
            .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, missingTag)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void attach_anonymous_returns_401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void attached_pair_is_projected_as_has_tag_edge_in_neo4j() {
    userClient()
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId)
        .retrieve()
        .toBodilessEntity();

    new TransactionTemplate(txManager).executeWithoutResult(s -> worker.drainBatch());

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              Long edgeCount =
                  neo4jClient
                      .query(
                          "MATCH (:Restaurant {id: $r})-[h:HAS_TAG]->(:Tag {id: $t})"
                              + " RETURN count(h) AS c")
                      .bindAll(Map.of("r", restaurantId.toString(), "t", tagId.toString()))
                      .fetchAs(Long.class)
                      .one()
                      .orElse(0L);
              assertThat(edgeCount).isEqualTo(1L);
            });
  }

  private RestClient userClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }

  private RestClient adminClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .build();
  }

  private record CreateTagPayload(String category, String slug, String label) {}
}
