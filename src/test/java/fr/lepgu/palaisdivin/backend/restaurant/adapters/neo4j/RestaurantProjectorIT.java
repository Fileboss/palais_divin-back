package fr.lepgu.palaisdivin.backend.restaurant.adapters.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CreateRestaurantRequest;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantResponse;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.OutboxWorker;
import java.time.Duration;
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

class RestaurantProjectorIT extends AbstractIntegrationTest {

  @LocalServerPort int port;

  @Autowired KeycloakContainer keycloak;
  @Autowired Neo4jClient neo4jClient;
  @Autowired JdbcClient jdbcClient;
  @Autowired RestaurantProjector projector;
  @Autowired OutboxWorker worker;
  @Autowired PlatformTransactionManager txManager;
  @Autowired BanApiClientStub banApiClient;

  private String userToken;

  @BeforeEach
  void setUp() {
    banApiClient.reset();

    userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, "palaisdivin", "palais-divin-frontend", "testuser", "testpassword");

    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    neo4jClient.query("MATCH (r:Restaurant) DETACH DELETE r").run();
  }

  @Test
  void postingARestaurantProjectsItIntoNeo4jAfterWorkerDrain() {
    RestClient client =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build();

    RestaurantResponse created =
        client
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);

    assertThat(created).isNotNull();

    new TransactionTemplate(txManager).executeWithoutResult(s -> worker.drainBatch());

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              Map<String, Object> node =
                  neo4jClient
                      .query(
                          "MATCH (r:Restaurant {id: $id}) "
                              + "RETURN r.name AS name, r.address AS address, "
                              + "r.latitude AS latitude, r.longitude AS longitude")
                      .bindAll(Map.of("id", created.id().toString()))
                      .fetch()
                      .one()
                      .orElse(null);
              assertThat(node).isNotNull();
              assertThat(node.get("name")).isEqualTo("Septime");
              assertThat(node.get("address")).isEqualTo("80 Rue de Charonne");
              assertThat((Double) node.get("latitude")).isEqualTo(48.8536);
              assertThat((Double) node.get("longitude")).isEqualTo(2.3795);
            });

    Long pending =
        jdbcClient
            .sql("SELECT count(*) FROM outbox_event WHERE status = 'PENDING'")
            .query(Long.class)
            .single();
    assertThat(pending).isZero();
  }

  @Test
  void projectingTheSameEventTwiceLeavesExactlyOneNode() {
    UUID id = UUID.randomUUID();
    JsonNode payload =
        new ObjectMapper()
            .createObjectNode()
            .put("id", id.toString())
            .put("name", "Le Chateaubriand")
            .put("address", "129 Av. Parmentier")
            .put("latitude", 48.8669)
            .put("longitude", 2.3713)
            .put("createdAt", "2026-05-28T10:00:00Z");

    projector.project("RestaurantCreated", payload);
    projector.project("RestaurantCreated", payload);

    Long count =
        neo4jClient
            .query("MATCH (r:Restaurant {id: $id}) RETURN count(r) AS c")
            .bindAll(Map.of("id", id.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    assertThat(count).isEqualTo(1L);
  }
}
