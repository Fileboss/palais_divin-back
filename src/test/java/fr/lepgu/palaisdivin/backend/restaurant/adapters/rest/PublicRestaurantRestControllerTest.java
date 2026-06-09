package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.LoadRestaurantThumbnailsUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListAffinityRankedRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.CountRestaurantReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListRestaurantTagsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicRestaurantRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicRestaurantRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean FindRestaurantUseCase findRestaurant;
  @MockitoBean ListRestaurantsUseCase listRestaurants;
  @MockitoBean ListRestaurantTagsUseCase listRestaurantTags;
  @MockitoBean LoadRestaurantThumbnailsUseCase loadThumbnails;
  @MockitoBean ListAffinityRankedRestaurantsUseCase listAffinityRanked;
  @MockitoBean CountRestaurantReviewsUseCase countReviews;
  @MockitoBean JwtDecoder jwtDecoder;

  @BeforeEach
  void stubEmptyTags() {
    when(listRestaurantTags.listFor(any(RestaurantId.class))).thenReturn(List.of());
    when(listRestaurantTags.listFor(anyCollection())).thenReturn(Map.of());
    when(loadThumbnails.load(anyCollection())).thenReturn(Map.of());
  }

  @Test
  void get_existingId_returns_200_without_auth() throws Exception {
    RestaurantId id = RestaurantId.newId();
    Restaurant found =
        new Restaurant(
            id,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT,
            null);
    when(findRestaurant.findById(id)).thenReturn(Optional.of(found));
    when(countReviews.countByRestaurant(id)).thenReturn(7L);

    mockMvc
        .perform(get("/api/v1/public/restaurants/{id}", id.value()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.name").value("Septime"))
        .andExpect(jsonPath("$.location.latitude").value(48.8536))
        .andExpect(jsonPath("$.location.longitude").value(2.3795))
        .andExpect(jsonPath("$.reviewCount").value(7));
  }

  @Test
  void get_missingId_returns_404_problem_detail() throws Exception {
    UUID id = UUID.randomUUID();
    when(findRestaurant.findById(new RestaurantId(id))).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/public/restaurants/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void list_noCursor_returnsEnvelope_withoutNextCursorWhenLastPage() throws Exception {
    Restaurant r1 = restaurant("Septime");
    Restaurant r2 = restaurant("Le Train Bleu");
    when(listRestaurants.list(null, 20, RestaurantFilter.none(), RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r1, r2), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].name").value("Septime"))
        .andExpect(jsonPath("$.data[1].name").value("Le Train Bleu"))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_withMoreAvailable_emitsNextCursor() throws Exception {
    Restaurant r1 = restaurant("Septime");
    Restaurant r2 = restaurant("Le Train Bleu");
    when(listRestaurants.list(null, 2, RestaurantFilter.none(), RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r1, r2), true));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.hasNext").value(true))
        .andExpect(
            jsonPath("$.page.nextCursor").value(Matchers.matchesPattern("^[A-Za-z0-9_\\-]+$")));
  }

  @Test
  void list_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_withSingleTag_passesFilterToService() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listRestaurants.list(
            null,
            20,
            new RestaurantFilter(List.of("burger"), null),
            RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("tag", "burger"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].name").value("Septime"));
  }

  @Test
  void list_withMultipleTags_passesAllSlugsInOrder() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listRestaurants.list(
            null,
            20,
            new RestaurantFilter(List.of("burger", "vegan"), null),
            RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("tag", "burger").param("tag", "vegan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_withInvalidSlugFormat_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("tag", "Invalid Slug"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_withTooManyTags_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants")
                .param("tag", "a")
                .param("tag", "b")
                .param("tag", "c")
                .param("tag", "d")
                .param("tag", "e")
                .param("tag", "f")
                .param("tag", "g")
                .param("tag", "h")
                .param("tag", "i")
                .param("tag", "j")
                .param("tag", "k"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_filterByName_passesTrimmedNameToUseCase() throws Exception {
    Restaurant r = restaurant("Le Bistrot");
    when(listRestaurants.list(
            null, 20, new RestaurantFilter(List.of(), "pizza"), RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("name", "  pizza  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_filterByBlankName_treatsAsNoFilter() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listRestaurants.list(null, 20, RestaurantFilter.none(), RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("name", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_filterByNameTooLong_returns400() throws Exception {
    String tooLong = "x".repeat(101);
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("name", tooLong))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_filterByNameAndTag_passesBothToUseCase() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listRestaurants.list(
            null,
            20,
            new RestaurantFilter(List.of("burger"), "septime"),
            RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("tag", "burger").param("name", "septime"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].name").value("Septime"));
  }

  @Test
  void list_emitsTagsPerRestaurantItem() throws Exception {
    Restaurant r1 = restaurant("Septime");
    Restaurant r2 = restaurant("Le Train Bleu");
    when(listRestaurants.list(null, 20, RestaurantFilter.none(), RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r1, r2), false));
    Tag food = new Tag(TagId.newId(), TagCategory.SPECIALTY, "burger", "Burger", FIXED_CREATED_AT);
    Tag regime = new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", FIXED_CREATED_AT);
    when(listRestaurantTags.listFor(anyCollection()))
        .thenReturn(Map.of(r1.id(), List.of(food), r2.id(), List.of(regime)));

    mockMvc
        .perform(get("/api/v1/public/restaurants"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].tags.length()").value(1))
        .andExpect(jsonPath("$.data[0].tags[0].slug").value("burger"))
        .andExpect(jsonPath("$.data[0].tags[0].category").value("SPECIALTY"))
        .andExpect(jsonPath("$.data[1].tags[0].slug").value("vegan"))
        .andExpect(jsonPath("$.data[1].tags[0].category").value("REGIME"));
  }

  @Test
  void list_sortByRating_passesSortToUseCase() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listRestaurants.list(null, 20, RestaurantFilter.none(), RestaurantSort.RATING_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("sort", "RATING_DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_sortByName_passesSortToUseCase() throws Exception {
    Restaurant r = restaurant("Allard");
    when(listRestaurants.list(null, 20, RestaurantFilter.none(), RestaurantSort.NAME_ASC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("sort", "NAME_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_unknownSortValue_returns400_badRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("sort", "NUKE"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/bad-request"));
  }

  @Test
  void list_sortByDistance_withAnchor_passesFilterAndSortThrough() throws Exception {
    Restaurant r = restaurant("Septime");
    Coordinates anchor = new Coordinates(48.8566, 2.3522);
    when(listRestaurants.list(
            null, 20, new RestaurantFilter(List.of(), null, anchor), RestaurantSort.DISTANCE_ASC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(
            get("/api/v1/public/restaurants")
                .param("sort", "DISTANCE_ASC")
                .param("lat", "48.8566")
                .param("lng", "2.3522"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_sortByDistance_withoutLat_returns400_missingAnchor() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants").param("sort", "DISTANCE_ASC").param("lng", "2.3522"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/missing-anchor"));
  }

  @Test
  void list_sortByDistance_withoutLng_returns400_missingAnchor() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants").param("sort", "DISTANCE_ASC").param("lat", "48.8566"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/missing-anchor"));
  }

  @Test
  void list_sortByDistance_neitherLatNorLng_returns400_missingAnchor() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("sort", "DISTANCE_ASC"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/missing-anchor"));
  }

  @Test
  void list_sortByCreatedAt_withAnchor_ignoresAnchor_returns200() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listRestaurants.list(
            null,
            20,
            new RestaurantFilter(List.of(), null, new Coordinates(48.8566, 2.3522)),
            RestaurantSort.CREATED_AT_DESC))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("lat", "48.8566").param("lng", "2.3522"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_latOutOfRange_returns400_validation() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants")
                .param("sort", "DISTANCE_ASC")
                .param("lat", "91")
                .param("lng", "2"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_lngOutOfRange_returns400_validation() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants")
                .param("sort", "DISTANCE_ASC")
                .param("lat", "48")
                .param("lng", "181"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_affinitySort_anonymous_returns400_problemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("sort", "AFFINITY_DESC"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type")
                .value("https://palaisdivin.lepgu.fr/problems/affinity-requires-auth"));
  }

  @Test
  void list_affinitySort_authenticated_delegatesToAffinityUseCase() throws Exception {
    Restaurant r = restaurant("Septime");
    when(listAffinityRanked.list(
            org.mockito.ArgumentMatchers.eq("kc-subject-affinity"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(20)))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(
            get("/api/v1/public/restaurants")
                .param("sort", "AFFINITY_DESC")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject("kc-subject-affinity"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].name").value("Septime"));
  }

  @Test
  void list_v4Cursor_withCreatedAtSort_returns400_invalidCursor() throws Exception {
    String token =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"d\":1234.5,\"id\":\"" + java.util.UUID.randomUUID() + "\",\"v\":4}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            get("/api/v1/public/restaurants")
                .param("sort", "CREATED_AT_DESC")
                .param("cursor", token))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"));
  }

  @Test
  void list_cursorFromCreatedAt_withRatingSort_returns400_invalidCursor() throws Exception {
    // build a v=1 createdAt cursor token then submit it with sort=RATING_DESC
    String token =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\""
                        + java.util.UUID.randomUUID()
                        + "\",\"v\":1}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            get("/api/v1/public/restaurants").param("sort", "RATING_DESC").param("cursor", token))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"));
  }

  private static Restaurant restaurant(String name) {
    return new Restaurant(
        RestaurantId.newId(),
        name,
        "addr",
        new Coordinates(48.8536, 2.3795),
        FIXED_CREATED_AT,
        null);
  }
}
