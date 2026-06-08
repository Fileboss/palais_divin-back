package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class RecommendationRestIT extends AbstractIntegrationTest {

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
  private UserId meId;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM user_connection").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    jdbcClient.sql("DELETE FROM app_user").update();
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();

    userToken =
        TestKeycloakTokens.passwordGrant(keycloak, REALM, FRONTEND_CLIENT, USERNAME, PASSWORD);
    String subject = TestKeycloakTokens.subjectOf(userToken);

    User me =
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
    meId = me.id();
    mergeUser(meId);
  }

  @Test
  void anonymousReturns401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/user/recommendations")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void invalidCursorReturns400ProblemDetail() {
    ResponseEntity<String> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/recommendations?cursor=not!base64!!")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).contains("/problems/invalid-cursor");
  }

  @Test
  void noFriends_returnsEmptyEnvelope() {
    ResponseEntity<RecommendationsPageResponse> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/recommendations")
            .retrieve()
            .toEntity(RecommendationsPageResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    RecommendationsPageResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).isEmpty();
    assertThat(body.page().hasNext()).isFalse();
    assertThat(body.page().nextCursor()).isNull();
  }

  @Test
  void walkPagesEndToEnd_overSeededGraph() {
    UserId friend = UserId.newId();
    mergeUser(friend);
    mergeKnows(meId, friend);

    RestaurantId rA = RestaurantId.newId();
    RestaurantId rB = RestaurantId.newId();
    RestaurantId rC = RestaurantId.newId();
    mergeRestaurant(rA, "A", "addr A", 48.85, 2.30);
    mergeRestaurant(rB, "B", "addr B", 48.86, 2.31);
    mergeRestaurant(rC, "C", "addr C", 48.87, 2.32);
    mergeRated(friend, rA, 5);
    mergeRated(friend, rB, 4);
    mergeRated(friend, rC, 3);

    RecommendationsPageResponse page1 =
        authedClient()
            .get()
            .uri("/api/v1/user/recommendations?size=2")
            .retrieve()
            .body(RecommendationsPageResponse.class);
    assertThat(page1).isNotNull();
    assertThat(page1.data()).hasSize(2);
    assertThat(page1.page().hasNext()).isTrue();
    assertThat(page1.page().nextCursor()).isNotBlank();
    assertThat(page1.data().get(0).affinity()).isEqualTo(5.0);
    assertThat(page1.data().get(1).affinity()).isEqualTo(4.0);

    RecommendationsPageResponse page2 =
        authedClient()
            .get()
            .uri("/api/v1/user/recommendations?size=2&cursor=" + page1.page().nextCursor())
            .retrieve()
            .body(RecommendationsPageResponse.class);
    assertThat(page2).isNotNull();
    assertThat(page2.data()).hasSize(1);
    assertThat(page2.page().hasNext()).isFalse();
    assertThat(page2.page().nextCursor()).isNull();
    assertThat(page2.data().getFirst().affinity()).isEqualTo(3.0);

    // Cursor sanity: the encoded affinity matches the last item of page1.
    RecommendationCursor decoded =
        RecommendationCursorCodec.decode(
            page1.page().nextCursor(),
            fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort.AFFINITY_DESC);
    RecommendationCursor.ByAffinity byAffinity = (RecommendationCursor.ByAffinity) decoded;
    assertThat(byAffinity.affinity()).isEqualTo(page1.data().getLast().affinity());
    assertThat(byAffinity.id().value()).isEqualTo(page1.data().getLast().id());
  }

  @Test
  void list_includeOwnDefault_excludesSelfRatedRestaurants() {
    UserId friend = UserId.newId();
    mergeUser(friend);
    mergeKnows(meId, friend);

    RestaurantId rated = RestaurantId.newId();
    RestaurantId unrated = RestaurantId.newId();
    mergeRestaurant(rated, "Rated", "addr R", 48.85, 2.30);
    mergeRestaurant(unrated, "Unrated", "addr U", 48.86, 2.31);
    mergeRated(friend, rated, 5);
    mergeRated(friend, unrated, 4);
    mergeRated(meId, rated, 3);

    RecommendationsPageResponse page =
        authedClient()
            .get()
            .uri("/api/v1/user/recommendations")
            .retrieve()
            .body(RecommendationsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data()).hasSize(1);
    assertThat(page.data().getFirst().id()).isEqualTo(unrated.value());
  }

  @Test
  void list_includeOwnTrue_returnsSelfRatedRestaurants() {
    UserId friend = UserId.newId();
    mergeUser(friend);
    mergeKnows(meId, friend);

    RestaurantId rated = RestaurantId.newId();
    RestaurantId unrated = RestaurantId.newId();
    mergeRestaurant(rated, "Rated", "addr R", 48.85, 2.30);
    mergeRestaurant(unrated, "Unrated", "addr U", 48.86, 2.31);
    mergeRated(friend, rated, 5);
    mergeRated(friend, unrated, 4);
    mergeRated(meId, rated, 3);

    RecommendationsPageResponse page =
        authedClient()
            .get()
            .uri("/api/v1/user/recommendations?includeOwn=true")
            .retrieve()
            .body(RecommendationsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data()).hasSize(2);
    assertThat(page.data().get(0).id()).isEqualTo(rated.value());
    assertThat(page.data().get(0).affinity()).isEqualTo(5.0);
    assertThat(page.data().get(1).id()).isEqualTo(unrated.value());
    assertThat(page.data().get(1).affinity()).isEqualTo(4.0);
  }

  // --- helpers -----------------------------------------------------------

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }

  private void mergeUser(UserId id) {
    neo4jClient
        .query("MERGE (u:User {id: $id})")
        .bindAll(Map.of("id", id.value().toString()))
        .run();
  }

  private void mergeRestaurant(
      RestaurantId id, String name, String address, double latitude, double longitude) {
    neo4jClient
        .query(
            """
            MERGE (r:Restaurant {id: $id})
            SET r.name = $name,
                r.address = $address,
                r.latitude = $latitude,
                r.longitude = $longitude
            """)
        .bindAll(
            Map.of(
                "id",
                id.value().toString(),
                "name",
                name,
                "address",
                address,
                "latitude",
                latitude,
                "longitude",
                longitude))
        .run();
  }

  private void mergeKnows(UserId source, UserId target) {
    neo4jClient
        .query(
            """
            MATCH (s:User {id: $s})
            MATCH (t:User {id: $t})
            MERGE (s)-[:KNOWS]->(t)
            """)
        .bindAll(Map.of("s", source.value().toString(), "t", target.value().toString()))
        .run();
  }

  private void mergeRated(UserId user, RestaurantId restaurant, int score) {
    neo4jClient
        .query(
            """
            MATCH (u:User {id: $u})
            MATCH (r:Restaurant {id: $r})
            MERGE (u)-[rated:RATED]->(r)
            SET rated.score = $score, rated.reviewId = $reviewId
            """)
        .bindAll(
            Map.of(
                "u",
                user.value().toString(),
                "r",
                restaurant.value().toString(),
                "score",
                score,
                "reviewId",
                UUID.randomUUID().toString()))
        .run();
  }
}
