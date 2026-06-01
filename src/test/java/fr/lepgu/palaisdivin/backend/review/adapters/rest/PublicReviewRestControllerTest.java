package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicReviewRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicReviewRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean ListReviewsUseCase listReviews;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void list_noCursor_returnsEnvelope_withoutNextCursorWhenLastPage() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    Review r1 = review(restaurantId, 5, "Great");
    Review r2 = review(restaurantId, 3, "Meh");
    when(listReviews.listByRestaurant(eq(new RestaurantId(restaurantId)), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants/{rid}/reviews", restaurantId))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].rating").value(5))
        .andExpect(jsonPath("$.data[0].comment").value("Great"))
        .andExpect(jsonPath("$.data[1].rating").value(3))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_withMoreAvailable_emitsNextCursor() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    Review r1 = review(restaurantId, 5, "Great");
    Review r2 = review(restaurantId, 4, "Solid");
    when(listReviews.listByRestaurant(eq(new RestaurantId(restaurantId)), isNull(), eq(2)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), true));

    mockMvc
        .perform(get("/api/v1/public/restaurants/{rid}/reviews", restaurantId).param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.hasNext").value(true))
        .andExpect(
            jsonPath("$.page.nextCursor").value(Matchers.matchesPattern("^[A-Za-z0-9_\\-]+$")));
  }

  @Test
  void list_decodesCursorAndForwardsToUseCase() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    ReviewCursor seed = new ReviewCursor(FIXED_CREATED_AT, ReviewId.newId());
    String token = ReviewCursorCodec.encode(seed);

    when(listReviews.listByRestaurant(
            eq(new RestaurantId(restaurantId)),
            org.mockito.ArgumentMatchers.any(ReviewCursor.class),
            eq(5)))
        .thenReturn(new CursorPage<>(List.of(), false));

    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews", restaurantId)
                .param("cursor", token)
                .param("size", "5"))
        .andExpect(status().isOk());

    ArgumentCaptor<ReviewCursor> captured = ArgumentCaptor.forClass(ReviewCursor.class);
    verify(listReviews)
        .listByRestaurant(eq(new RestaurantId(restaurantId)), captured.capture(), eq(5));
    org.assertj.core.api.Assertions.assertThat(captured.getValue()).isEqualTo(seed);
  }

  @Test
  void list_invalidCursor_returns400_problemDetail() throws Exception {
    UUID restaurantId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews", restaurantId)
                .param("cursor", "not!base64!!"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid cursor"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"));
  }

  @Test
  void list_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews", UUID.randomUUID()).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_sizeBelowMin_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews", UUID.randomUUID()).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_unknownSortValue_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews", UUID.randomUUID())
                .param("sort", "RATING_DESC"))
        .andExpect(status().isBadRequest());
  }

  private static Review review(UUID restaurantId, int rating, String comment) {
    return new Review(
        ReviewId.newId(),
        new RestaurantId(restaurantId),
        UserId.newId(),
        rating,
        comment,
        FIXED_CREATED_AT);
  }
}
