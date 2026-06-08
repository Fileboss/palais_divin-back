package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CreateRestaurantRequest;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantResponse;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.RestaurantsPageResponse;
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
import java.util.List;
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

class PublicPhotoRestIT extends AbstractIntegrationTest {

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

    restaurantId = createRestaurant("Septime", "80 Rue de Charonne");
  }

  @Test
  void galleryReturnsPhotosOldestFirstWithPresignedUrls()
      throws IOException, InterruptedException {
    UUID first = registerPhoto(restaurantId, "first".getBytes());
    Thread.sleep(20);
    UUID second = registerPhoto(restaurantId, "second".getBytes());
    Thread.sleep(20);
    UUID third = registerPhoto(restaurantId, "third".getBytes());

    PhotosPageResponse page =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants/{rid}/photos", restaurantId)
            .retrieve()
            .body(PhotosPageResponse.class);

    assertThat(page).isNotNull();
    List<UUID> ids = page.data().stream().map(PhotoSummaryResponse::id).toList();
    assertThat(ids).containsExactly(first, second, third);
    assertThat(page.data())
        .allSatisfy(
            s -> {
              assertThat(s.url()).isNotBlank();
              assertThat(s.expiresAt()).isAfter(Instant.now());
            });
    assertThat(page.page().hasNext()).isFalse();
    assertThat(page.page().nextCursor()).isNull();
  }

  @Test
  void galleryPaginatesByCursor() throws IOException, InterruptedException {
    UUID first = registerPhoto(restaurantId, "first".getBytes());
    Thread.sleep(20);
    UUID second = registerPhoto(restaurantId, "second".getBytes());
    Thread.sleep(20);
    UUID third = registerPhoto(restaurantId, "third".getBytes());

    PhotosPageResponse pageOne =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants/{rid}/photos?size=2", restaurantId)
            .retrieve()
            .body(PhotosPageResponse.class);

    assertThat(pageOne).isNotNull();
    assertThat(pageOne.data()).hasSize(2);
    assertThat(pageOne.page().hasNext()).isTrue();
    assertThat(pageOne.page().nextCursor()).isNotBlank();

    PhotosPageResponse pageTwo =
        anonClient()
            .get()
            .uri(
                "/api/v1/public/restaurants/{rid}/photos?size=2&cursor=" + pageOne.page().nextCursor(),
                restaurantId)
            .retrieve()
            .body(PhotosPageResponse.class);

    assertThat(pageTwo).isNotNull();
    assertThat(pageTwo.data()).hasSize(1);
    assertThat(pageTwo.data().get(0).id()).isEqualTo(third);
    assertThat(pageTwo.page().hasNext()).isFalse();
    assertThat(pageOne.data().stream().map(PhotoSummaryResponse::id).toList())
        .containsExactly(first, second);
  }

  @Test
  void galleryUrlIsServeable() throws IOException, InterruptedException {
    byte[] payload = "image-bytes".getBytes();
    registerPhoto(restaurantId, payload);

    PhotosPageResponse page =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants/{rid}/photos", restaurantId)
            .retrieve()
            .body(PhotosPageResponse.class);

    String url = page.data().get(0).url();
    HttpResponse<byte[]> fetched =
        HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(URI.create(url)).GET().build(), BodyHandlers.ofByteArray());

    assertThat(fetched.statusCode()).isEqualTo(200);
    assertThat(fetched.body()).isEqualTo(payload);
  }

  @Test
  void galleryReturns404WhenRestaurantUnknown() {
    UUID unknown = UUID.randomUUID();
    ResponseEntity<String> resp =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants/{rid}/photos", unknown)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void restaurantListCarriesOldestPhotoAsThumbnail() throws IOException, InterruptedException {
    UUID first = registerPhoto(restaurantId, "first".getBytes());
    Thread.sleep(20);
    registerPhoto(restaurantId, "second".getBytes());

    RestaurantsPageResponse page =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants?size=100")
            .retrieve()
            .body(RestaurantsPageResponse.class);

    RestaurantResponse fetched =
        page.data().stream().filter(r -> r.id().equals(restaurantId)).findFirst().orElseThrow();
    assertThat(fetched.thumbnail()).isNotNull();
    assertThat(fetched.thumbnail().id()).isEqualTo(first);
    assertThat(fetched.thumbnail().url()).isNotBlank();
    assertThat(fetched.thumbnail().expiresAt()).isAfter(Instant.now());
  }

  @Test
  void restaurantDetailCarriesThumbnail() throws IOException, InterruptedException {
    UUID first = registerPhoto(restaurantId, "first".getBytes());

    RestaurantResponse fetched =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants/" + restaurantId)
            .retrieve()
            .body(RestaurantResponse.class);

    assertThat(fetched.thumbnail()).isNotNull();
    assertThat(fetched.thumbnail().id()).isEqualTo(first);
  }

  @Test
  void restaurantListHasNullThumbnailWhenNoPhotos() {
    RestaurantsPageResponse page =
        anonClient()
            .get()
            .uri("/api/v1/public/restaurants?size=100")
            .retrieve()
            .body(RestaurantsPageResponse.class);

    RestaurantResponse fetched =
        page.data().stream().filter(r -> r.id().equals(restaurantId)).findFirst().orElseThrow();
    assertThat(fetched.thumbnail()).isNull();
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

  private RestClient anonClient() {
    return RestClient.create("http://localhost:" + port);
  }
}
