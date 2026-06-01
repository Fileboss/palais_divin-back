package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.OutboxWorker;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

class SignupRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String PASSWORD = "P4ssw0rd!";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired InvitationRepositoryPort invitations;
  @Autowired UserRepositoryPort users;
  @Autowired Neo4jClient neo4jClient;
  @Autowired OutboxWorker worker;
  @Autowired PlatformTransactionManager txManager;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM user_connection").update();
    jdbcClient.sql("DELETE FROM app_user").update();
    jdbcClient.sql("DELETE FROM invitation").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void endToEnd_adminInvites_userSignsUp_canLogIn_andProjectsToNeo4j() {
    String email = "happy-" + UUID.randomUUID() + "@example.test";
    String token = mintInvitationAsAdmin();

    SignupResponse signed =
        anonClient()
            .post()
            .uri("/api/v1/public/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SignupRequest(token, email, "Happy User", PASSWORD))
            .retrieve()
            .toEntity(SignupResponse.class)
            .getBody();

    assertThat(signed).isNotNull();
    assertThat(signed.id()).isNotNull();
    assertThat(signed.email()).isEqualTo(email);
    assertThat(signed.displayName()).isEqualTo("Happy User");
    assertThat(signed.createdAt()).isNotNull();

    Optional<User> persisted = users.findBySubject(extractSubjectFromPostgres(signed.id()));
    assertThat(persisted).isPresent();
    assertThat(persisted.get().email()).isEqualTo(email);
    assertThat(persisted.get().displayName()).isEqualTo("Happy User");
    assertThat(UUID.fromString(persisted.get().subject())).isNotNull();

    Optional<Invitation> consumedInv = invitations.findByToken(new InvitationToken(token));
    assertThat(consumedInv).isPresent();
    assertThat(consumedInv.get().consumedAt()).isNotNull();

    String userToken =
        TestKeycloakTokens.passwordGrant(keycloak, REALM, FRONTEND_CLIENT, email, PASSWORD);
    assertThat(userToken).isNotBlank();

    ResponseEntity<String> listResp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build()
            .get()
            .uri("/api/v1/user/restaurants")
            .retrieve()
            .toEntity(String.class);
    assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    new TransactionTemplate(txManager).executeWithoutResult(s -> worker.drainBatch());

    assertThat(countUserNodes(signed.id())).isEqualTo(1L);
  }

  @Test
  void unknownToken_returns404_problemDetail() {
    ResponseEntity<String> resp =
        anonClient()
            .post()
            .uri("/api/v1/public/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new SignupRequest(
                    "unknown-" + UUID.randomUUID(),
                    "unknown-" + UUID.randomUUID() + "@example.test",
                    "Unknown",
                    PASSWORD))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void expiredToken_returns410_invitationNotUsable_reasonExpired() {
    Instant createdAt = Instant.now().minusSeconds(7200);
    Instant expiresAt = Instant.now().minusSeconds(60);
    InvitationToken token = InvitationToken.newToken();
    invitations.save(new Invitation(InvitationId.newId(), token, expiresAt, null, createdAt));

    ResponseEntity<String> resp =
        anonClient()
            .post()
            .uri("/api/v1/public/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new SignupRequest(
                    token.value(),
                    "expired-" + UUID.randomUUID() + "@example.test",
                    "Expired",
                    PASSWORD))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(resp.getBody()).contains("/problems/invitation-not-usable");
    assertThat(resp.getBody()).contains("EXPIRED");
  }

  @Test
  void alreadyConsumedToken_returns410_invitationNotUsable_reasonConsumed() {
    Instant createdAt = Instant.now().minusSeconds(60);
    Instant expiresAt = Instant.now().plusSeconds(3600);
    InvitationToken token = InvitationToken.newToken();
    invitations.save(
        new Invitation(InvitationId.newId(), token, expiresAt, Instant.now(), createdAt));

    ResponseEntity<String> resp =
        anonClient()
            .post()
            .uri("/api/v1/public/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new SignupRequest(
                    token.value(),
                    "consumed-" + UUID.randomUUID() + "@example.test",
                    "Consumed",
                    PASSWORD))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(resp.getBody()).contains("/problems/invitation-not-usable");
    assertThat(resp.getBody()).contains("ALREADY_CONSUMED");
  }

  @Test
  void duplicateEmail_returns409_conflict() {
    String email = "dup-" + UUID.randomUUID() + "@example.test";
    String firstToken = mintInvitationAsAdmin();
    String secondToken = mintInvitationAsAdmin();

    anonClient()
        .post()
        .uri("/api/v1/public/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new SignupRequest(firstToken, email, "First", PASSWORD))
        .retrieve()
        .toEntity(SignupResponse.class);

    ResponseEntity<String> resp =
        anonClient()
            .post()
            .uri("/api/v1/public/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SignupRequest(secondToken, email, "Second", PASSWORD))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/conflict");
  }

  private String mintInvitationAsAdmin() {
    String adminToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testadmin", "testadmin");
    InvitationResponse resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .build()
            .post()
            .uri("/api/v1/admin/invitations")
            .retrieve()
            .body(InvitationResponse.class);
    return tokenQueryParam(resp.signupUrl());
  }

  private RestClient anonClient() {
    return RestClient.create("http://localhost:" + port);
  }

  private String extractSubjectFromPostgres(UUID userId) {
    return users
        .findById(new fr.lepgu.palaisdivin.backend.user.domain.model.UserId(userId))
        .orElseThrow(() -> new AssertionError("user " + userId + " not in Postgres"))
        .subject();
  }

  private long countUserNodes(UUID userId) {
    return neo4jClient
        .query("MATCH (u:User {id: $id}) RETURN count(u) AS c")
        .bindAll(Map.of("id", userId.toString()))
        .fetchAs(Long.class)
        .one()
        .orElse(0L);
  }

  private static String tokenQueryParam(String url) {
    String query = URI.create(url).getQuery();
    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2 && "token".equals(kv[0])) {
        return kv[1];
      }
    }
    throw new AssertionError("no token query param in " + url);
  }
}
