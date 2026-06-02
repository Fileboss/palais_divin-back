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

class PhotoDownloadUrlRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";
  private static final byte[] PAYLOAD = "image-bytes".getBytes();

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

    restaurantId = createRestaurant("Septime", "80 Rue de Charonne");
  }

  @Test
  void getReturns200WithDownloadUrlThatServesBytes() throws IOException, InterruptedException {
    UUID photoId = registerPhoto(restaurantId, PAYLOAD);

    ResponseEntity<PhotoDownloadUrlResponse> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/restaurants/{rid}/photos/{pid}/download-url", restaurantId, photoId)
            .retrieve()
            .toEntity(PhotoDownloadUrlResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    PhotoDownloadUrlResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.objectKey()).contains(restaurantId.toString());
    assertThat(body.downloadUrl()).isNotBlank();
    assertThat(body.expiresAt()).isAfter(Instant.now());

    HttpResponse<byte[]> fetched =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(body.downloadUrl())).GET().build(),
                BodyHandlers.ofByteArray());

    assertThat(fetched.statusCode()).isEqualTo(200);
    assertThat(fetched.body()).isEqualTo(PAYLOAD);
  }

  @Test
  void getReturns404WhenPhotoUnknown() {
    UUID unknown = UUID.randomUUID();
    ResponseEntity<String> resp =
        authedClient()
            .get()
            .uri("/api/v1/user/restaurants/{rid}/photos/{pid}/download-url", restaurantId, unknown)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void getReturns404WhenPhotoBelongsToOtherRestaurant() throws IOException, InterruptedException {
    UUID otherRestaurantId = createRestaurant("L'Avant Comptoir", "3 Carrefour de l'Odéon");
    UUID otherPhotoId = registerPhoto(otherRestaurantId, PAYLOAD);

    ResponseEntity<String> resp =
        authedClient()
            .get()
            .uri(
                "/api/v1/user/restaurants/{rid}/photos/{pid}/download-url",
                restaurantId,
                otherPhotoId)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void anonymousReturns401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri(
                "/api/v1/user/restaurants/{rid}/photos/{pid}/download-url",
                restaurantId,
                UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  private UUID createRestaurant(String name, String address) {
    RestaurantResponse seeded =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest(name, address))
            .retrieve()
            .body(RestaurantResponse.class);
    return seeded.id();
  }

  private UUID registerPhoto(UUID rid, byte[] payload) throws IOException, InterruptedException {
    PhotoUploadUrlResponse mint =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos/upload-url", rid)
            .retrieve()
            .body(PhotoUploadUrlResponse.class);

    HttpResponse<Void> put =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(mint.uploadUrl()))
                    .PUT(BodyPublishers.ofByteArray(payload))
                    .build(),
                BodyHandlers.discarding());
    assertThat(put.statusCode()).isEqualTo(200);

    PhotoResponse registered =
        authedClient()
            .post()
            .uri("/api/v1/user/restaurants/{rid}/photos", rid)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterPhotoRequest(mint.objectKey(), "image/jpeg"))
            .retrieve()
            .body(PhotoResponse.class);
    return registered.id();
  }

  private RestClient authedClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
