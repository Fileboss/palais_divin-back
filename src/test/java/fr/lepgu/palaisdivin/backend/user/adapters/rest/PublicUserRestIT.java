package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class PublicUserRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";

  @LocalServerPort int port;
  @Autowired UserRepositoryPort users;
  @Autowired JdbcClient jdbcClient;
  @Autowired KeycloakContainer keycloak;

  @BeforeEach
  void clean() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    jdbcClient.sql("DELETE FROM user_connection").update();
    jdbcClient.sql("DELETE FROM app_user").update();
  }

  @Test
  void get_returns200WithProfile_whenUserExists() {
    Instant createdAt = Instant.parse("2026-05-27T10:15:30Z");
    User saved =
        users.save(
            new User(
                UserId.newId(),
                "subj-alice-" + UUID.randomUUID(),
                "alice-" + UUID.randomUUID() + "@example.test",
                "Alice",
                createdAt));

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/users/{userId}", saved.id().value())
            .retrieve()
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("\"id\":\"" + saved.id().value() + "\"");
    assertThat(body).contains("\"displayName\":\"Alice\"");
    assertThat(body).contains("\"createdAt\":\"" + createdAt.toString() + "\"");
    assertThat(body).contains("\"isFollowedByMe\":null");
    assertThat(body).doesNotContain("\"subject\"");
    assertThat(body).doesNotContain("\"email\"");
  }

  @Test
  void get_returns404Problem_whenUnknownId() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/users/{userId}", UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void get_returns400Problem_whenMalformedId() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/users/not-a-uuid")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/bad-request");
  }

  @Test
  void get_authedViewerFollowingTarget_returnsTrue() {
    String userToken =
        TestKeycloakTokens.passwordGrant(keycloak, REALM, FRONTEND_CLIENT, USERNAME, PASSWORD);
    String viewerSubject = TestKeycloakTokens.subjectOf(userToken);
    User viewer = ensureUser(viewerSubject, USERNAME + "@example.test", "Viewer");
    User target = saveTarget("Alice");
    jdbcClient
        .sql(
            "INSERT INTO user_connection (id, source_user_id, target_user_id, created_at)"
                + " VALUES (?, ?, ?, now())")
        .params(UUID.randomUUID(), viewer.id().value(), target.id().value())
        .update();

    String body =
        authedClient(userToken)
            .get()
            .uri("/api/v1/public/users/{userId}", target.id().value())
            .retrieve()
            .body(String.class);

    assertThat(body).isNotNull();
    assertThat(body).contains("\"isFollowedByMe\":true");
  }

  @Test
  void get_authedViewerNotFollowingTarget_returnsFalse() {
    String userToken =
        TestKeycloakTokens.passwordGrant(keycloak, REALM, FRONTEND_CLIENT, USERNAME, PASSWORD);
    String viewerSubject = TestKeycloakTokens.subjectOf(userToken);
    ensureUser(viewerSubject, USERNAME + "@example.test", "Viewer");
    User target = saveTarget("Bob");

    String body =
        authedClient(userToken)
            .get()
            .uri("/api/v1/public/users/{userId}", target.id().value())
            .retrieve()
            .body(String.class);

    assertThat(body).isNotNull();
    assertThat(body).contains("\"isFollowedByMe\":false");
  }

  @Test
  void get_authedSelfLookup_returnsFollowedNull() {
    String userToken =
        TestKeycloakTokens.passwordGrant(keycloak, REALM, FRONTEND_CLIENT, USERNAME, PASSWORD);
    String viewerSubject = TestKeycloakTokens.subjectOf(userToken);
    User viewer = ensureUser(viewerSubject, USERNAME + "@example.test", "Viewer");

    String body =
        authedClient(userToken)
            .get()
            .uri("/api/v1/public/users/{userId}", viewer.id().value())
            .retrieve()
            .body(String.class);

    assertThat(body).isNotNull();
    assertThat(body).contains("\"isFollowedByMe\":null");
  }

  private User ensureUser(String subject, String email, String displayName) {
    return users
        .findBySubject(subject)
        .orElseGet(
            () -> users.save(new User(UserId.newId(), subject, email, displayName, Instant.now())));
  }

  private User saveTarget(String displayName) {
    return users.save(
        new User(
            UserId.newId(),
            "subj-" + UUID.randomUUID(),
            displayName.toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
            displayName,
            Instant.now()));
  }

  private RestClient authedClient(String token) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .build();
  }
}
