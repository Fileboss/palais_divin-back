package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
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

class RestaurantAffinityRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired UserRepositoryPort users;
  @Autowired RestaurantRepositoryPort restaurants;
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
    UUID anyId = UUID.randomUUID();
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/user/restaurants/" + anyId + "/affinity")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void unknownRestaurant_returns404_problemDetail() {
    UUID missingId = UUID.randomUUID();
    ResponseEntity<String> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/restaurants/" + missingId + "/affinity")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void seededGraph_returnsAffinity_endToEnd() {
    Restaurant r =
        restaurants.save(
            new Restaurant(
                RestaurantId.newId(),
                "Septime",
                "80 Rue de Charonne",
                new Coordinates(48.8536, 2.3795),
                Instant.now(),
                null));
    RestaurantId restId = r.id();

    UserId friend = UserId.newId();
    UserId fof = UserId.newId();
    mergeUser(friend);
    mergeUser(fof);
    mergeRestaurant(restId, "Septime", "80 Rue de Charonne", 48.8536, 2.3795);
    mergeKnows(meId, friend);
    mergeKnows(friend, fof);
    mergeRated(friend, restId, 5);
    mergeRated(fof, restId, 3);

    RestaurantAffinityResponse body =
        authedClient()
            .get()
            .uri("/api/v1/user/restaurants/" + restId.value() + "/affinity")
            .retrieve()
            .body(RestaurantAffinityResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.restaurantId()).isEqualTo(restId.value());
    assertThat(body.affinity()).isEqualTo(8.0);
    assertThat(body.recommenderCount()).isEqualTo(2);
  }

  @Test
  void restaurantExistsButNoFriendRated_returnsZeroAffinity() {
    Restaurant r =
        restaurants.save(
            new Restaurant(
                RestaurantId.newId(),
                "Clamato",
                "80 Rue de Charonne",
                new Coordinates(48.8536, 2.3795),
                Instant.now(),
                null));
    RestaurantId restId = r.id();

    RestaurantAffinityResponse body =
        authedClient()
            .get()
            .uri("/api/v1/user/restaurants/" + restId.value() + "/affinity")
            .retrieve()
            .body(RestaurantAffinityResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.restaurantId()).isEqualTo(restId.value());
    assertThat(body.affinity()).isZero();
    assertThat(body.recommenderCount()).isZero();
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
