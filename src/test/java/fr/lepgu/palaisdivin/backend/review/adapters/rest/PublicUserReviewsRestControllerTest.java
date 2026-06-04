package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(PublicUserReviewsRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicUserReviewsRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-06-04T10:00:00Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean ListReviewsUseCase listReviews;
  @MockitoBean RestaurantRepositoryPort restaurants;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void list_returns200WithEnrichedRestaurant_onHappyPath() throws Exception {
    UUID authorUuid = UUID.randomUUID();
    UserId authorId = new UserId(authorUuid);
    RestaurantId rid = RestaurantId.newId();
    ReviewId reviewId = ReviewId.newId();
    Review r = new Review(reviewId, rid, authorId, 5, "Top", FIXED_CREATED_AT);
    Restaurant rest =
        new Restaurant(
            rid,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT.minusSeconds(3600),
            null);
    when(listReviews.listByAuthor(eq(authorId), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(r), false));
    when(restaurants.findByIds(anyCollection())).thenReturn(Map.of(rid, rest));

    mockMvc
        .perform(get("/api/v1/public/users/{uid}/reviews", authorUuid))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].reviewId").value(reviewId.value().toString()))
        .andExpect(jsonPath("$.data[0].rating").value(5))
        .andExpect(jsonPath("$.data[0].comment").value("Top"))
        .andExpect(jsonPath("$.data[0].restaurant.id").value(rid.value().toString()))
        .andExpect(jsonPath("$.data[0].restaurant.name").value("Septime"))
        .andExpect(jsonPath("$.data[0].restaurant.address").value("80 Rue de Charonne"))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_returnsEmptyPage_onUnknownAuthor() throws Exception {
    UUID authorUuid = UUID.randomUUID();
    when(listReviews.listByAuthor(eq(new UserId(authorUuid)), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(), false));
    when(restaurants.findByIds(anyCollection())).thenReturn(Map.of());

    mockMvc
        .perform(get("/api/v1/public/users/{uid}/reviews", authorUuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_encodesNextCursor_whenHasNext() throws Exception {
    UUID authorUuid = UUID.randomUUID();
    UserId authorId = new UserId(authorUuid);
    RestaurantId rid = RestaurantId.newId();
    Review r1 = new Review(ReviewId.newId(), rid, authorId, 5, "A", FIXED_CREATED_AT);
    Review r2 =
        new Review(ReviewId.newId(), rid, authorId, 4, "B", FIXED_CREATED_AT.minusSeconds(10));
    Restaurant rest =
        new Restaurant(rid, "Septime", "80", new Coordinates(48.8, 2.3), FIXED_CREATED_AT, null);
    when(listReviews.listByAuthor(eq(authorId), isNull(), eq(2)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), true));
    when(restaurants.findByIds(anyCollection())).thenReturn(Map.of(rid, rest));

    mockMvc
        .perform(get("/api/v1/public/users/{uid}/reviews", authorUuid).param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.hasNext").value(true))
        .andExpect(
            jsonPath("$.page.nextCursor").value(Matchers.matchesPattern("^[A-Za-z0-9_\\-]+$")));
  }

  @Test
  void list_decodesCursorAndForwardsToUseCase() throws Exception {
    UUID authorUuid = UUID.randomUUID();
    UserId authorId = new UserId(authorUuid);
    ReviewCursor seed = new ReviewCursor(FIXED_CREATED_AT, ReviewId.newId());
    String token = ReviewCursorCodec.encode(seed);
    when(listReviews.listByAuthor(
            eq(authorId), org.mockito.ArgumentMatchers.any(ReviewCursor.class), eq(5)))
        .thenReturn(new CursorPage<>(List.of(), false));
    when(restaurants.findByIds(anyCollection())).thenReturn(Map.of());

    mockMvc
        .perform(
            get("/api/v1/public/users/{uid}/reviews", authorUuid)
                .param("cursor", token)
                .param("size", "5"))
        .andExpect(status().isOk());

    ArgumentCaptor<ReviewCursor> captured = ArgumentCaptor.forClass(ReviewCursor.class);
    verify(listReviews).listByAuthor(eq(authorId), captured.capture(), eq(5));
    org.assertj.core.api.Assertions.assertThat(captured.getValue()).isEqualTo(seed);
  }

  @Test
  void list_invalidCursor_returns400_problemDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/users/{uid}/reviews", UUID.randomUUID())
                .param("cursor", "not!base64!!"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"));
  }

  @Test
  void list_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/users/{uid}/reviews", UUID.randomUUID()).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_sizeBelowMin_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/users/{uid}/reviews", UUID.randomUUID()).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_malformedUserId_returns400_problemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/users/not-a-uuid/reviews"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/bad-request"));
  }
}
