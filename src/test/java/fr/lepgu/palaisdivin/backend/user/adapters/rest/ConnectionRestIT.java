package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class ConnectionRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired UserRepositoryPort users;
  @Autowired JdbcClient jdbcClient;
  @Autowired Neo4jClient neo4jClient;
  @Autowired OutboxWorker worker;
  @Autowired PlatformTransactionManager txManager;

  private String userToken;
  private UserId sourceUserId;
  private UserId targetUserId;

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

    User sourceUser =
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
    sourceUserId = sourceUser.id();

    User targetUser =
        users.save(
            new User(
                UserId.newId(),
                "other-subject",
                "other@example.test",
                "Other User",
                Instant.now()));
    targetUserId = targetUser.id();
  }

  @Test
  void postReturns201WithLocationAndBody() {
    ResponseEntity<ConnectionResponse> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/connections/{targetId}", targetUserId.value())
            .retrieve()
            .toEntity(ConnectionResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getHeaders().getLocation()).isNotNull();
    assertThat(resp.getHeaders().getLocation().toString())
        .endsWith("/api/v1/user/connections/" + targetUserId.value());
    ConnectionResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.sourceUserId()).isEqualTo(sourceUserId.value());
    assertThat(body.targetUserId()).isEqualTo(targetUserId.value());
  }

  @Test
  void duplicatePostReturns200WithSameId() {
    ConnectionResponse first =
        authedClient()
            .post()
            .uri("/api/v1/user/connections/{targetId}", targetUserId.value())
            .retrieve()
            .body(ConnectionResponse.class);

    ResponseEntity<ConnectionResponse> dup =
        authedClient()
            .post()
            .uri("/api/v1/user/connections/{targetId}", targetUserId.value())
            .retrieve()
            .toEntity(ConnectionResponse.class);

    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(dup.getBody()).isNotNull();
    assertThat(dup.getBody().id()).isEqualTo(first.id());
  }

  @Test
  void targetMissingReturns404() {
    UUID missingId = UUID.randomUUID();
    ResponseEntity<String> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/connections/{targetId}", missingId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void selfConnectionReturns422() {
    ResponseEntity<String> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/connections/{targetId}", sourceUserId.value())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode().value()).isEqualTo(422);
    assertThat(resp.getBody()).contains("/problems/self-connection");
  }

  @Test
  void anonymousReturns401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/user/connections/{targetId}", targetUserId.value())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void postedConnectionIsProjectedAsKnowsEdgeInNeo4j() {
    authedClient()
        .post()
        .uri("/api/v1/user/connections/{targetId}", targetUserId.value())
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
                          "MATCH (s:User {id: $sourceId})-[k:KNOWS]->(t:User {id: $targetId})"
                              + " RETURN count(k) AS c")
                      .bindAll(
                          Map.of(
                              "sourceId", sourceUserId.value().toString(),
                              "targetId", targetUserId.value().toString()))
                      .fetchAs(Long.class)
                      .one()
                      .orElse(0L);
              assertThat(edgeCount).isEqualTo(1L);
            });
  }

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
