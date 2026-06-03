package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class PublicUserRestIT extends AbstractIntegrationTest {

  @LocalServerPort int port;
  @Autowired UserRepositoryPort users;
  @Autowired JdbcClient jdbcClient;

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
}
