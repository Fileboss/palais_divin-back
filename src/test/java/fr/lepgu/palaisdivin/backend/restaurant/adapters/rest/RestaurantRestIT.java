package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanResponse;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class RestaurantRestIT extends AbstractIntegrationTest {

  @LocalServerPort int port;

  @Autowired KeycloakContainer keycloak;

  @Autowired BanApiClientStub banApiClient;

  private String userToken;

  @BeforeEach
  void resetBanStub() {
    banApiClient.reset();
  }

  @Test
  void postThenGetReturnsTheSameRestaurant() {
    RestClient client = authedClient();

    CreateRestaurantRequest req = new CreateRestaurantRequest("Septime", "80 Rue de Charonne");

    ResponseEntity<RestaurantResponse> postResp =
        client
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(req)
            .retrieve()
            .toEntity(RestaurantResponse.class);

    assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(postResp.getHeaders().getLocation()).isNotNull();
    RestaurantResponse created = postResp.getBody();
    assertThat(created).isNotNull();
    assertThat(created.id()).isNotNull();
    assertThat(created.name()).isEqualTo("Septime");
    assertThat(created.address()).isEqualTo("80 Rue de Charonne");
    assertThat(created.location().latitude()).isEqualTo(48.8536);
    assertThat(created.location().longitude()).isEqualTo(2.3795);

    RestaurantResponse fetched =
        client
            .get()
            .uri("/api/v1/public/restaurants/" + created.id())
            .retrieve()
            .body(RestaurantResponse.class);

    assertThat(fetched).isNotNull();
    assertThat(fetched.id()).isEqualTo(created.id());
    assertThat(fetched.name()).isEqualTo(created.name());
    assertThat(fetched.address()).isEqualTo(created.address());
    assertThat(fetched.location()).isEqualTo(created.location());
    assertThat(fetched.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.MILLIS));
  }

  @Test
  void postUnresolvableAddressReturns422ProblemDetail() {
    banApiClient.setResponseFor("zzzz unknown address zzzz", new BanResponse(List.of()));

    RestClient client = authedClient();
    CreateRestaurantRequest req =
        new CreateRestaurantRequest("Septime", "zzzz unknown address zzzz");

    ResponseEntity<String> resp =
        client
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(req)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (r, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode().value()).isEqualTo(422);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/unresolvable-address");
  }

  @Test
  void listWalksAllPagesByCursor() {
    RestClient client = authedClient();

    Set<UUID> postedIds = new HashSet<>();
    for (int i = 0; i < 25; i++) {
      CreateRestaurantRequest req = new CreateRestaurantRequest("r-" + i, "address-for-r-" + i);
      RestaurantResponse created =
          client
              .post()
              .uri("/api/v1/user/restaurants")
              .contentType(MediaType.APPLICATION_JSON)
              .body(req)
              .retrieve()
              .body(RestaurantResponse.class);
      postedIds.add(created.id());
    }

    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/restaurants?size=10"
              : "/api/v1/public/restaurants?size=10&cursor=" + cursor;
      RestaurantsPageResponse body =
          client.get().uri(path).retrieve().body(RestaurantsPageResponse.class);
      assertThat(body).isNotNull();
      assertThat(body.page().size()).isEqualTo(10);
      body.data().forEach(r -> collected.add(r.id()));
      pages++;
      if (!body.page().hasNext()) {
        assertThat(body.page().nextCursor()).isNull();
        break;
      }
      cursor = body.page().nextCursor();
      assertThat(cursor).isNotBlank();
      if (pages > 10) {
        throw new AssertionError("paging did not terminate");
      }
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).containsAll(postedIds);
  }

  @Test
  void repeatedAddressIsServedFromCacheAndHitsBanOnlyOnce() {
    RestClient client = authedClient();
    String address = "cache-hit address " + UUID.randomUUID();
    CreateRestaurantRequest req = new CreateRestaurantRequest("Cache Test", address);

    for (int i = 0; i < 3; i++) {
      client
          .post()
          .uri("/api/v1/user/restaurants")
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(RestaurantResponse.class);
    }

    assertThat(banApiClient.callCountFor(address.toLowerCase())).isEqualTo(1);
  }

  @Test
  void unauthenticated_post_returns_401_problem_detail() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);

    ResponseEntity<String> resp =
        unauthed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("X", "Y"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  private RestClient authedClient() {
    if (userToken == null) {
      userToken =
          TestKeycloakTokens.passwordGrant(
              keycloak, "palaisdivin", "palais-divin-frontend", "testuser", "testpassword");
    }
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .build();
  }
}
