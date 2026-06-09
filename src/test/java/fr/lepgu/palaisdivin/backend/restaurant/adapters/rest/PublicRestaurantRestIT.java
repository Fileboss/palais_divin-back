package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import java.util.ArrayList;
import java.util.Base64;
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
    assertThat(fetched.reviewCount()).isEqualTo(0L);
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
  void list_filterByName_returnsMatchingRestaurants() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String matchingName = "Le Bistrot " + suffix;
    String nonMatchingName = "Le Train Bleu " + suffix;

    RestClient authed = authedClient();
    UUID matching =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest(matchingName, "addr-bistrot-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID nonMatching =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest(nonMatchingName, "addr-train-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&name=bistrot+" + suffix)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data().stream().map(RestaurantResponse::id).toList())
        .contains(matching)
        .doesNotContain(nonMatching);
  }

  @Test
  void list_filterByName_isCaseInsensitive() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String name = "Le Bistrot " + suffix;

    RestClient authed = authedClient();
    UUID id =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest(name, "addr-case-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse upper =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&name=BISTROT+" + suffix)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(upper).isNotNull();
    assertThat(upper.data().stream().map(RestaurantResponse::id).toList()).contains(id);
  }

  @Test
  void list_filterByNameAndTag_andsThem() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String burgerSlug = "lpn-burger-" + suffix;

    RestClient authed = authedClient();
    UUID burgerBistro =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new CreateRestaurantRequest(
                    "Burger Bistro " + suffix, "addr-burgerbistro-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID burgerHouse =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new CreateRestaurantRequest("Burger House " + suffix, "addr-burgerhouse-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID regularBistro =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new CreateRestaurantRequest(
                    "Regular Bistro " + suffix, "addr-regularbistro-" + suffix))
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
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", burgerBistro, burgerId)
        .retrieve()
        .toBodilessEntity();
    authed
        .post()
        .uri("/api/v1/user/restaurants/{r}/tags/{t}", burgerHouse, burgerId)
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&tag=" + burgerSlug + "&name=bistro+" + suffix)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data().stream().map(RestaurantResponse::id).toList())
        .contains(burgerBistro)
        .doesNotContain(burgerHouse, regularBistro);
  }

  @Test
  void list_unknownName_returnsEmptyPage() {
    RestClient authed = authedClient();
    authed
        .post()
        .uri("/api/v1/user/restaurants")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateRestaurantRequest("any-unknown", "addr-any-unknown"))
        .retrieve()
        .toBodilessEntity();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=10&name=zzz-no-such-name-" + UUID.randomUUID())
            .retrieve()
            .body(RestaurantsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data()).isEmpty();
    assertThat(page.page().hasNext()).isFalse();
    assertThat(page.page().nextCursor()).isNull();
  }

  @Test
  void list_nameOver100Chars_returns400() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    String tooLong = "x".repeat(101);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?name=" + tooLong)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

  @Test
  void list_sortByRating_returnsRatedFirst_thenUnrated() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    RestClient authed = authedClient();

    UUID hi =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Rated-Hi-" + suffix, "addr-hi-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID lo =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Rated-Lo-" + suffix, "addr-lo-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID unrated =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Unrated-" + suffix, "addr-un-" + suffix))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    jdbcClient.sql("UPDATE restaurant SET avg_rating = 4.50 WHERE id = ?").param(hi).update();
    jdbcClient.sql("UPDATE restaurant SET avg_rating = 2.00 WHERE id = ?").param(lo).update();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&sort=RATING_DESC")
            .retrieve()
            .body(RestaurantsPageResponse.class);

    List<UUID> ids = page.data().stream().map(RestaurantResponse::id).toList();
    int hiIdx = ids.indexOf(hi);
    int loIdx = ids.indexOf(lo);
    int unratedIdx = ids.indexOf(unrated);
    assertThat(hiIdx).isGreaterThanOrEqualTo(0);
    assertThat(loIdx).isGreaterThan(hiIdx);
    assertThat(unratedIdx).isGreaterThan(loIdx);
  }

  @Test
  void list_sortByRating_paginatesAcrossNullBoundary() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String prefix = "zrate-" + suffix;
    RestClient authed = authedClient();
    Set<UUID> seeded = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      UUID id =
          authed
              .post()
              .uri("/api/v1/user/restaurants")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CreateRestaurantRequest(prefix + "-rated-" + i, "addr-r-" + i))
              .retrieve()
              .body(RestaurantResponse.class)
              .id();
      jdbcClient
          .sql("UPDATE restaurant SET avg_rating = ? WHERE id = ?")
          .param(new java.math.BigDecimal(String.valueOf(4.0 - i * 0.5)))
          .param(id)
          .update();
      seeded.add(id);
    }
    for (int i = 0; i < 3; i++) {
      UUID id =
          authed
              .post()
              .uri("/api/v1/user/restaurants")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CreateRestaurantRequest(prefix + "-unrated-" + i, "addr-u-" + i))
              .retrieve()
              .body(RestaurantResponse.class)
              .id();
      seeded.add(id);
    }

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/restaurants?size=2&sort=RATING_DESC&name=" + prefix
              : "/api/v1/public/restaurants?size=2&sort=RATING_DESC&name="
                  + prefix
                  + "&cursor="
                  + cursor;
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
    assertThat(new HashSet<>(collected)).isEqualTo(seeded);
  }

  @Test
  void list_sortByName_returnsAlphabetical() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    RestClient authed = authedClient();
    UUID carmen =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Carmen-" + suffix, "addr-c"))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID allard =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Allard-" + suffix, "addr-a"))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    UUID benoit =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest("Benoit-" + suffix, "addr-b"))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?size=100&sort=NAME_ASC&name=" + suffix)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    List<UUID> ids = page.data().stream().map(RestaurantResponse::id).toList();
    assertThat(ids).containsExactly(allard, benoit, carmen);
  }

  @Test
  void list_sortByName_paginatesNoOverlap() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    RestClient authed = authedClient();
    Set<UUID> seeded = new HashSet<>();
    String[] names = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf"};
    for (String n : names) {
      UUID id =
          authed
              .post()
              .uri("/api/v1/user/restaurants")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CreateRestaurantRequest("zpag-" + suffix + "-" + n, "addr-" + n))
              .retrieve()
              .body(RestaurantResponse.class)
              .id();
      seeded.add(id);
    }

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/restaurants?size=3&sort=NAME_ASC&name=zpag-" + suffix
              : "/api/v1/public/restaurants?size=3&sort=NAME_ASC&name=zpag-"
                  + suffix
                  + "&cursor="
                  + cursor;
      RestaurantsPageResponse body =
          unauthed.get().uri(path).retrieve().body(RestaurantsPageResponse.class);
      assertThat(body).isNotNull();
      body.data().forEach(r -> collected.add(r.id()));
      pages++;
      if (!body.page().hasNext()) break;
      cursor = body.page().nextCursor();
      if (pages > 10) throw new AssertionError("paging did not terminate");
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).isEqualTo(seeded);
  }

  @Test
  void list_v1CursorWithRatingSort_returns400_invalidCursorProblemDetail() {
    String token =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?sort=RATING_DESC&cursor=" + token)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/invalid-cursor");
  }

  @Test
  void list_sortByDistance_returnsNearestFirst_andSurfacesDistanceMetres() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String prefix = "zdist-" + suffix;
    RestClient authed = authedClient();
    UUID near = postAt(authed, prefix + "-near", "addr-n", 48.8530, 2.3500);
    UUID mid = postAt(authed, prefix + "-mid", "addr-m", 48.8530, 2.3520);
    UUID far = postAt(authed, prefix + "-far", "addr-f", 48.8530, 2.3540);

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    RestaurantsPageResponse page =
        unauthed
            .get()
            .uri(
                "/api/v1/public/restaurants?size=100&sort=DISTANCE_ASC&lat=48.8530&lng=2.3499&name="
                    + prefix)
            .retrieve()
            .body(RestaurantsPageResponse.class);

    List<UUID> ids = page.data().stream().map(RestaurantResponse::id).toList();
    assertThat(ids).containsExactly(near, mid, far);
    assertThat(page.data())
        .allSatisfy(r -> assertThat(r.distanceMetres()).isNotNull().isGreaterThanOrEqualTo(0.0));
    for (int i = 1; i < page.data().size(); i++) {
      assertThat(page.data().get(i).distanceMetres())
          .isGreaterThan(page.data().get(i - 1).distanceMetres());
    }
  }

  @Test
  void list_sortByDistance_paginatesAcrossPages_noOverlap() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String prefix = "zdwalk-" + suffix;
    RestClient authed = authedClient();
    Set<UUID> seeded = new HashSet<>();
    for (int i = 0; i < 7; i++) {
      seeded.add(postAt(authed, prefix + "-" + i, "addr-" + i, 48.8530, 2.3499 + 0.001 * i));
    }

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/restaurants?size=2&sort=DISTANCE_ASC&lat=48.8530&lng=2.3499&name="
                  + prefix
              : "/api/v1/public/restaurants?size=2&sort=DISTANCE_ASC&lat=48.8530&lng=2.3499&name="
                  + prefix
                  + "&cursor="
                  + cursor;
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
    assertThat(new HashSet<>(collected)).isEqualTo(seeded);
  }

  @Test
  void list_sortByDistance_missingLat_returns400_missingAnchorProblemDetail() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?sort=DISTANCE_ASC&lng=2.3499")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/missing-anchor");
  }

  @Test
  void list_sortByDistance_missingLng_returns400_missingAnchorProblemDetail() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?sort=DISTANCE_ASC&lat=48.8530")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).contains("/problems/missing-anchor");
  }

  @Test
  void list_sortByDistance_latOutOfRange_returns400() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?sort=DISTANCE_ASC&lat=91&lng=2")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void list_v4CursorWithCreatedAtSort_returns400_invalidCursorProblemDetail() {
    String token =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"d\":42.0,\"id\":\"" + UUID.randomUUID() + "\",\"v\":4}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?sort=CREATED_AT_DESC&cursor=" + token)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).contains("/problems/invalid-cursor");
  }

  private UUID postAt(RestClient authed, String name, String address, double lat, double lng) {
    UUID id =
        authed
            .post()
            .uri("/api/v1/user/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateRestaurantRequest(name, address))
            .retrieve()
            .body(RestaurantResponse.class)
            .id();
    jdbcClient
        .sql(
            "UPDATE restaurant SET location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography WHERE"
                + " id = ?")
        .param(lng)
        .param(lat)
        .param(id)
        .update();
    return id;
  }

  @Test
  void list_invalidSortEnum_returns400_badRequestProblemDetail() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants?sort=NUKE")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).contains("/problems/bad-request");
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
