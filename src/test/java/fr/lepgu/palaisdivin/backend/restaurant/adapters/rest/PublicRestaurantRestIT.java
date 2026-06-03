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
  private String adminToken;

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
  void list_filterByTag_returnsOnlyTaggedRestaurants() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String burgerSlug = "lp-burger-" + suffix;

    RestClient authed = authedClient();
    UUID r1 =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("WithTag-" + suffix, "addr-with-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID r2 =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("NoTag-" + suffix, "addr-no-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();

    UUID burgerId =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("FOOD", burgerSlug, "Burger"))
            .retrieve()
            .body(TagResponseDto.class)
            .id();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", r1, burgerId)
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&tag=" + burgerSlug)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data().stream().map(RestaurantResponse::id).toList())
        .contains(r1)
        .doesNotContain(r2);
  }

  @Test
  void list_filterByTwoTags_andsThem() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String burgerSlug = "lp-burger-" + suffix;
    String veganSlug = "lp-vegan-" + suffix;

    RestClient authed = authedClient();
    UUID rBoth =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Both-" + suffix, "addr-both-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID rBurgerOnly =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Burger-" + suffix, "addr-burger-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();

    UUID burgerId =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("FOOD", burgerSlug, "Burger"))
            .retrieve()
            .body(TagResponseDto.class)
            .id();
    UUID veganId =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("REGIME", veganSlug, "Vegan"))
            .retrieve()
            .body(TagResponseDto.class)
            .id();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", rBoth, burgerId)
        .retrieve()
        .toBodilessEntity();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", rBoth, veganId)
        .retrieve()
        .toBodilessEntity();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", rBurgerOnly, burgerId)
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&tag=" + burgerSlug + "&tag=" + veganSlug)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data().stream().map(RestaurantResponse::id).toList())
        .contains(rBoth)
        .doesNotContain(rBurgerOnly);
  }

  @Test
  void list_unknownSlug_returnsEmptyPage() {
    RestClient authed = authedClient();
    authed
        .post()
        .uri("/api/v1/user/restaurants")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateRestaurantRequest("any", "addr-any"))
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri(
                "/api/v1/public/restaurants?size=10&tag=does-not-exist-"
                    + UUID.randomUUID().toString().substring(0, 8))
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data()).isEmpty();
    assertThat(page.page().hasNext()).isFalse();
    assertThat(page.page().nextCursor()).isNull();
  }

  @Test
  void list_includesTagsInItemsInCategoryThenSlugOrder() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String foodSlug = "li-food-" + suffix;
    String regimeSlug = "li-regime-" + suffix;

    RestClient authed = authedClient();
    UUID r =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("WithTags-" + suffix, "addr-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID foodId =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("FOOD", foodSlug, "Food"))
            .retrieve()
            .body(TagResponseDto.class)
            .id();
    UUID regimeId =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new TagPayload("REGIME", regimeSlug, "Regime"))
            .retrieve()
            .body(TagResponseDto.class)
            .id();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", r, regimeId)
        .retrieve()
        .toBodilessEntity();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", r, foodId)
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100")
            .retrieve()
            .body(RestaurantsPageResponse.class);

    RestaurantResponse fetched =
        page.data().stream().filter(it -> it.id().equals(r)).findFirst().orElseThrow();
    assertThat(fetched.tags())
        .extracting(
            RestaurantResponse.TagSummary::category,
            RestaurantResponse.TagSummary::slug,
            RestaurantResponse.TagSummary::label)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(TagCategory.FOOD, foodSlug, "Food"),
            org.assertj.core.groups.Tuple.tuple(TagCategory.REGIME, regimeSlug, "Regime"));
  }

  @Test
  void list_invalidSlugFormat_returns400() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?tag=Bad%20Slug")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

  private RestClient adminClient() {
    if (adminToken == null) {
      adminToken =
          TestKeycloakTokens.passwordGrant(
              keycloak, "palaisdivin", "palais-divin-frontend", "testadmin", "testadmin");
    }
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .build();
  }
}
