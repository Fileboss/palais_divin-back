package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class PublicRestaurantRestIT extends AbstractIntegrationTest {

  @LocalServerPort int port;

  @Autowired KeycloakContainer keycloak;

  @Autowired BanApiClientStub banApiClient;

  @Autowired JdbcClient jdbcClient;

  private String userToken;

  @BeforeEach
  void resetBanStub() {
    banApiClient.reset();
    jdbcClient.sql("DELETE FROM restaurant_tag").update();
    jdbcClient.sql("DELETE FROM tag").update();
  }

  @Test
  void list_isReachableWithoutAuth_andReturnsSeededData() {
    RestClient authed = authedClient();
    Set<UUID> postedIds = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      RestaurantResponse created =
          authed
              .post()
              .uri("/api/v1/user/restaurants")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CreateRestaurantRequest("pub-" + i, "addr-pub-" + i))
              .retrieve()
              .body(RestaurantResponse.class);
      postedIds.add(created.id());
    }

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100")
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data().stream().map(RestaurantResponse::id).toList()).containsAll(postedIds);
  }

  @Test
  void get_byId_isReachableWithoutAuth() {
    RestClient authed = authedClient();
    RestaurantResponse created =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantResponse fetched =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants/" + created.id())
            .retrieve()
            .body(RestaurantResponse.class);

    assertThat(fetched).isNotNull();
    assertThat(fetched.id()).isEqualTo(created.id());
    assertThat(fetched.name()).isEqualTo("Septime");
    assertThat(fetched.tags()).isEmpty();
  }

  @Test
  void get_byId_returns_attached_tags_in_category_then_slug_order() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String foodSlug = "pub-food-" + suffix;
    String regimeSlug = "pub-regime-" + suffix;
    String foodLabel = "Food " + suffix;
    String regimeLabel = "Regime " + suffix;

    RestClient authed = authedClient();
    RestaurantResponse created =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Septime", "80 Rue de Charonne"))
            .retrieve()
            .body(RestaurantResponse.class);

    String adminToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, "palaisdivin", "palais-divin-frontend", "testadmin", "testadmin");
    RestClient admin =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .build();
    UUID foodId =
        admin
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("FOOD", foodSlug, foodLabel))
            .retrieve()
            .body(TagResponseDto.class)
            .id();
    UUID regimeId =
        admin
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("REGIME", regimeSlug, regimeLabel))
            .retrieve()
            .body(TagResponseDto.class)
            .id();

    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", created.id(), regimeId)
        .retrieve()
        .toBodilessEntity();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", created.id(), foodId)
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantResponse fetched =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants/" + created.id())
            .retrieve()
            .body(RestaurantResponse.class);

    assertThat(fetched).isNotNull();
    assertThat(fetched.tags())
        .extracting(
            RestaurantResponse.TagSummary::category,
            RestaurantResponse.TagSummary::slug,
            RestaurantResponse.TagSummary::label)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(TagCategory.FOOD, foodSlug, foodLabel),
            org.assertj.core.groups.Tuple.tuple(TagCategory.REGIME, regimeSlug, regimeLabel));
  }

  private record TagPayload(String category, String slug, String label) {}

  private record TagResponseDto(UUID id) {}

  @Test
  void get_missingId_returns_404_problem_detail_withoutAuth() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants/" + UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void list_walks_all_pages_by_cursor_withoutAuth() {
    RestClient authed = authedClient();
    Set<UUID> postedIds = new HashSet<>();
    for (int i = 0; i < 12; i++) {
      RestaurantResponse created =
          authed
              .post()
              .uri("/api/v1/user/restaurants")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CreateRestaurantRequest("paged-" + i, "addr-paged-" + i))
              .retrieve()
              .body(RestaurantResponse.class);
      postedIds.add(created.id());
    }

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/restaurants?size=5"
              : "/api/v1/public/restaurants?size=5&cursor=" + cursor;
      RestaurantsPageResponse body =
          unauthed.get().uri(path).retrieve().body(RestaurantsPageResponse.class);
      assertThat(body).isNotNull();
      body.data().forEach(r -> collected.add(r.id()));
      pages++;
      if (!body.page().hasNext()) break;
      cursor = body.page().nextCursor();
      assertThat(cursor).isNotBlank();
      if (pages > 10) throw new AssertionError("paging did not terminate");
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).containsAll(postedIds);
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
