package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRecommendationsUseCase;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(RecommendationRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RecommendationRestControllerTest {

  private static final String SUBJECT = "kc-subject-recs";

  @Autowired MockMvc mockMvc;

  @MockitoBean GetRecommendationsUseCase getRecommendations;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  private static Recommendation reco(double affinity, int recommenderCount) {
    return new Recommendation(
        RestaurantId.newId(),
        "Septime",
        "80 rue de Charonne",
        48.8,
        2.3,
        affinity,
        recommenderCount);
  }

  @Test
  void list_default_returnsEnvelope_without_nextCursor_whenLastPage() throws Exception {
    Recommendation r1 = reco(9.0, 2);
    Recommendation r2 = reco(5.0, 1);
    when(getRecommendations.list(
            eq(SUBJECT),
            isNull(),
            eq(20),
            eq(false),
            eq(RecommendationSort.AFFINITY_DESC),
            isNull(),
            any(RestaurantFilter.class)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), false));

    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].name").value("Septime"))
        .andExpect(jsonPath("$.data[0].affinity").value(9.0))
        .andExpect(jsonPath("$.data[0].recommenderCount").value(2))
        .andExpect(jsonPath("$.data[0].location.latitude").value(48.8))
        .andExpect(jsonPath("$.data[0].location.longitude").value(2.3))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_withMoreAvailable_emitsNextCursor() throws Exception {
    Recommendation r1 = reco(9.0, 2);
    Recommendation r2 = reco(7.0, 1);
    when(getRecommendations.list(
            eq(SUBJECT),
            isNull(),
            eq(2),
            eq(false),
            eq(RecommendationSort.AFFINITY_DESC),
            isNull(),
            any(RestaurantFilter.class)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), true));

    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.hasNext").value(true))
        .andExpect(
            jsonPath("$.page.nextCursor").value(Matchers.matchesPattern("^[A-Za-z0-9_\\-]+$")));
  }

  @Test
  void list_decodesCursorAndForwardsToUseCase() throws Exception {
    RecommendationCursor.ByAffinity seed =
        new RecommendationCursor.ByAffinity(7.5, RestaurantId.newId());
    String token = RecommendationCursorCodec.encode(seed);

    when(getRecommendations.list(
            eq(SUBJECT),
            any(RecommendationCursor.class),
            eq(5),
            eq(false),
            eq(RecommendationSort.AFFINITY_DESC),
            isNull(),
            any(RestaurantFilter.class)))
        .thenReturn(new CursorPage<>(List.of(), false));

    mockMvc
        .perform(
            get("/api/v1/user/recommendations")
                .with(userJwt())
                .param("cursor", token)
                .param("size", "5"))
        .andExpect(status().isOk());

    ArgumentCaptor<RecommendationCursor> captured =
        ArgumentCaptor.forClass(RecommendationCursor.class);
    verify(getRecommendations)
        .list(
            eq(SUBJECT),
            captured.capture(),
            eq(5),
            eq(false),
            eq(RecommendationSort.AFFINITY_DESC),
            isNull(),
            any(RestaurantFilter.class));
    org.assertj.core.api.Assertions.assertThat(captured.getValue()).isEqualTo(seed);
  }

  @Test
  void list_invalidCursor_returns400_problemDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/user/recommendations").with(userJwt()).param("cursor", "not!base64!!"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"));
  }

  @Test
  void list_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_sizeBelowMin_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_unknownSortValue_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("sort", "NUKE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_includeOwnTrue_isForwardedToUseCase() throws Exception {
    when(getRecommendations.list(
            eq(SUBJECT),
            isNull(),
            eq(20),
            eq(true),
            eq(RecommendationSort.AFFINITY_DESC),
            isNull(),
            any(RestaurantFilter.class)))
        .thenReturn(new CursorPage<>(List.of(), false));

    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("includeOwn", "true"))
        .andExpect(status().isOk());

    verify(getRecommendations)
        .list(
            eq(SUBJECT),
            isNull(),
            eq(20),
            eq(true),
            eq(RecommendationSort.AFFINITY_DESC),
            isNull(),
            any(RestaurantFilter.class));
  }

  @Test
  void list_sortByRating_forwardsToUseCase() throws Exception {
    Recommendation r =
        new Recommendation(
            RestaurantId.newId(), "Septime", "addr", 48.8, 2.3, 5.0, 1, 4.7, null, null);
    when(getRecommendations.list(
            eq(SUBJECT),
            isNull(),
            eq(20),
            eq(false),
            eq(RecommendationSort.RATING_DESC),
            isNull(),
            any(RestaurantFilter.class)))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("sort", "RATING_DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].avgRating").value(4.7));
  }

  @Test
  void list_sortByDistance_missingLatLng_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/recommendations").with(userJwt()).param("sort", "DISTANCE_ASC"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/missing-anchor"));
  }

  @Test
  void list_sortByDistance_withLatLng_forwardsAnchor() throws Exception {
    Recommendation r =
        new Recommendation(
            RestaurantId.newId(), "Septime", "addr", 48.8, 2.3, 5.0, 1, null, 120.0, null);
    when(getRecommendations.list(
            eq(SUBJECT),
            isNull(),
            eq(20),
            eq(false),
            eq(RecommendationSort.DISTANCE_ASC),
            eq(new Coordinates(48.8566, 2.3522)),
            any(RestaurantFilter.class)))
        .thenReturn(new CursorPage<>(List.of(r), false));

    mockMvc
        .perform(
            get("/api/v1/user/recommendations")
                .with(userJwt())
                .param("sort", "DISTANCE_ASC")
                .param("lat", "48.8566")
                .param("lng", "2.3522"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].distanceMetres").value(120.0));
  }

  @Test
  void list_anonymous_returns401_problemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/recommendations"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @SuppressWarnings("unused")
  private static UUID anyId() {
    return UUID.randomUUID();
  }
}
