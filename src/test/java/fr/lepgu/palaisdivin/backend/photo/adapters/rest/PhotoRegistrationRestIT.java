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

class PhotoRegistrationRestIT extends AbstractIntegrationTest {

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
    jdbcClient.sql("DELETE FROM photo").update();
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

  private PhotoUploadUrlResponse mintAndUpload() throws IOException, InterruptedException {
    PhotoUploadUrlResponse mint =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos/upload-url", restaurantId)
            .retrieve()
            .body(PhotoUploadUrlResponse.class);

    HttpResponse<Void> put =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(mint.uploadUrl()))
                    .PUT(BodyPublishers.ofByteArray("image-bytes".getBytes()))
                    .build(),
                BodyHandlers.discarding());
    assertThat(put.statusCode()).isEqualTo(200);
    return mint;
  }

  @Test
  void registerReturns201WithLocationAndBody() throws IOException, InterruptedException {
    PhotoUploadUrlResponse mint = mintAndUpload();

    ResponseEntity<PhotoResponse> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(mint.objectKey(), "image/jpeg"))
            .retrieve()
            .toEntity(PhotoResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getHeaders().getLocation()).isNotNull();
    PhotoResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.restaurantId()).isEqualTo(restaurantId);
    assertThat(body.objectKey()).isEqualTo(mint.objectKey());
    assertThat(body.contentType()).isEqualTo("image/jpeg");
    assertThat(resp.getHeaders().getLocation().toString())
        .endsWith("/api/v1/user/restaurants/" + restaurantId + "/photos/" + body.id());

    Long rowCount =
        jdbcClient
            .sql("SELECT count(*) FROM photo WHERE id = ?")
            .param(body.id())
            .query(Long.class)
            .single();
    assertThat(rowCount).isEqualTo(1L);
  }

  @Test
  void idempotencyKeyReplaysSameId() throws IOException, InterruptedException {
    PhotoUploadUrlResponse mint = mintAndUpload();
    String key = "key-" + UUID.randomUUID();

    PhotoResponse first =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(mint.objectKey(), "image/jpeg"))
            .retrieve()
            .body(PhotoResponse.class);

    PhotoResponse replay =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(mint.objectKey(), "image/jpeg"))
            .retrieve()
            .body(PhotoResponse.class);

    assertThat(replay.id()).isEqualTo(first.id());
    Long rowCount = jdbcClient.sql("SELECT count(*) FROM photo").query(Long.class).single();
    assertThat(rowCount).isEqualTo(1L);
  }

  @Test
  void duplicateObjectKeyWithoutIdempotencyKeyReturns409()
      throws IOException, InterruptedException {
    PhotoUploadUrlResponse mint = mintAndUpload();

    authedClient()
        .post()
        .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new RegisterPhotoRequest(mint.objectKey(), "image/jpeg"))
        .retrieve()
        .toBodilessEntity();

    ResponseEntity<String> dup =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(mint.objectKey(), "image/jpeg"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(dup.getBody()).contains("/problems/conflict");
  }

  @Test
  void badObjectKeyShapeReturns400() {
    ResponseEntity<String> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest("not-a-restaurants-key", "image/jpeg"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).contains("/problems/invalid-object-key");
  }

  @Test
  void unknownRestaurantReturns404() {
    UUID missing = UUID.randomUUID();
    String objectKey = "restaurants/" + missing + "/" + UUID.randomUUID();
    ResponseEntity<String> resp =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", missing)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(objectKey, "image/jpeg"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void anonymousReturns401() {
    String objectKey = "restaurants/" + restaurantId + "/" + UUID.randomUUID();
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", restaurantId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(objectKey, "image/jpeg"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
