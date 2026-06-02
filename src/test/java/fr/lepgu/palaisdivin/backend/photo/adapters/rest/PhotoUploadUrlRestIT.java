package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CreateRestaurantRequest;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantResponse;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
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

class PhotoUploadUrlRestIT extends AbstractIntegrationTest {

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
  void postReturnsUploadUrlThatAcceptsActualBytes() throws IOException, InterruptedException {
    ResponseEntity<PhotoUploadUrlResponse> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos/upload-url", restaurantId)
            .retrieve()
            .toEntity(PhotoUploadUrlResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    PhotoUploadUrlResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.objectKey()).startsWith("restaurants/" + restaurantId + "/");
    assertThat(body.uploadUrl()).contains("palaisdivin-photos");
    assertThat(body.expiresAt()).isAfter(Instant.now());

    byte[] payload = "image-bytes".getBytes();
    HttpResponse<Void> put =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(body.uploadUrl()))
                    .PUT(BodyPublishers.ofByteArray(payload))
                    .build(),
                BodyHandlers.discarding());
    assertThat(put.statusCode()).isEqualTo(200);
  }

  @Test
  void anonymousReturns401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos/upload-url", restaurantId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void unknownRestaurantReturns404() {
    UUID missing = UUID.randomUUID();
    ResponseEntity<String> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos/upload-url", missing)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
    assertThat(resp.getBody()).contains(missing.toString());
  }

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
