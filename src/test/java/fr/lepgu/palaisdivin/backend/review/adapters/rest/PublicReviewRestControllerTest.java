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
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.ReviewNotFoundException;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.FindReviewByAuthorUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.LookupUsersUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
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
  @MockitoBean FindReviewByAuthorUseCase findReviewByAuthor;
  @MockitoBean LookupUsersUseCase lookupUsers;
  @MockitoBean JwtDecoder jwtDecoder;

  @BeforeEach
  void stubEmptyAuthors() {
    when(lookupUsers.findByIds(anyCollection())).thenReturn(Map.of());
  }

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
  void getByAuthor_found_returns200_withBody() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Review r = review(restaurantId, 4, "Nice");
    when(findReviewByAuthor.findByRestaurantAndAuthor(
            new RestaurantId(restaurantId), new UserId(authorId)))
        .thenReturn(r);

    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews/author/{aid}", restaurantId, authorId))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.rating").value(4))
        .andExpect(jsonPath("$.comment").value("Nice"));
  }

  @Test
  void getByAuthor_notFound_returns404() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    when(findReviewByAuthor.findByRestaurantAndAuthor(
            new RestaurantId(restaurantId), new UserId(authorId)))
        .thenThrow(new ReviewNotFoundException(new ReviewId(UUID.randomUUID())));

    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews/author/{aid}", restaurantId, authorId))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void list_unknownSortValue_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/restaurants/{rid}/reviews", UUID.randomUUID())
                .param("sort", "RATING_DESC"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_emitsAuthorDisplayNamePerItem() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UserId authorA = UserId.newId();
    UserId authorB = UserId.newId();
    Review r1 = reviewBy(restaurantId, authorA, 5, "Great");
    Review r2 = reviewBy(restaurantId, authorB, 3, "Meh");
    when(listReviews.listByRestaurant(eq(new RestaurantId(restaurantId)), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), false));
    when(lookupUsers.findByIds(anyCollection()))
        .thenReturn(
            Map.of(
                authorA, user(authorA, "Alice"),
                authorB, user(authorB, "Bob")));

    mockMvc
        .perform(get("/api/v1/public/restaurants/{rid}/reviews", restaurantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].authorId").value(authorA.value().toString()))
        .andExpect(jsonPath("$.data[0].authorDisplayName").value("Alice"))
        .andExpect(jsonPath("$.data[1].authorId").value(authorB.value().toString()))
        .andExpect(jsonPath("$.data[1].authorDisplayName").value("Bob"));
  }

  @Test
  void list_unknownAuthor_emitsNullDisplayName() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    Review r = review(restaurantId, 5, "Great");
    when(listReviews.listByRestaurant(eq(new RestaurantId(restaurantId)), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(r), false));
    when(lookupUsers.findByIds(anyCollection())).thenReturn(Map.of());

    mockMvc
        .perform(get("/api/v1/public/restaurants/{rid}/reviews", restaurantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].authorDisplayName").value(Matchers.nullValue()));
  }

  @Test
  void getByAuthor_emitsAuthorDisplayName() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UserId authorId = UserId.newId();
    Review r = reviewBy(restaurantId, authorId, 4, "Nice");
    when(findReviewByAuthor.findByRestaurantAndAuthor(new RestaurantId(restaurantId), authorId))
        .thenReturn(r);
    when(lookupUsers.findByIds(anyCollection()))
        .thenReturn(Map.of(authorId, user(authorId, "Charlie")));

    mockMvc
        .perform(
            get(
                "/api/v1/public/restaurants/{rid}/reviews/author/{aid}",
                restaurantId,
                authorId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorDisplayName").value("Charlie"));
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

  private static Review reviewBy(UUID restaurantId, UserId authorId, int rating, String comment) {
    return new Review(
        ReviewId.newId(),
        new RestaurantId(restaurantId),
        authorId,
        rating,
        comment,
        FIXED_CREATED_AT);
  }

  private static User user(UserId id, String displayName) {
    return new User(
        id, "kc-" + id.value(), id.value() + "@example.test", displayName, FIXED_CREATED_AT);
  }
}
