package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.ReviewNotFoundException;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.CreateReviewUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.UpdateReviewUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ReviewRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ReviewRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-31T12:00:00Z");
  private static final String SUBJECT = "kc-subject-xyz";

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateReviewUseCase createReview;
  @MockitoBean UpdateReviewUseCase updateReview;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
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

  @Test
  void post_validPayload_returns_201_with_location_and_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    Review created = review(restaurantId, 4, "Great");
    when(createReview.create(
            eq(SUBJECT),
            eq(new RestaurantId(restaurantId)),
            eq(4),
            eq("Great"),
            eq(Optional.empty())))
        .thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 4, "comment": "Great" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    Matchers.endsWith(
                        "/api/v1/user/restaurants/"
                            + restaurantId
                            + "/reviews/"
                            + created.id().value())))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(created.id().value().toString()))
        .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
        .andExpect(jsonPath("$.rating").value(4))
        .andExpect(jsonPath("$.comment").value("Great"))
        .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT.toString()));
  }

  @Test
  void post_ratingAboveMax_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", UUID.randomUUID())
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 6, "comment": "x" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'rating')]").exists());
  }

  @Test
  void post_ratingBelowMin_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", UUID.randomUUID())
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 0 }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'rating')]").exists());
  }

  @Test
  void post_anonymous_returns_401_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 4 }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void post_restaurantMissing_returns_404_problem() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    when(createReview.create(
            eq(SUBJECT), eq(new RestaurantId(restaurantId)), anyInt(), any(), any()))
        .thenThrow(new RestaurantNotFoundException(new RestaurantId(restaurantId)));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 4 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"))
        .andExpect(jsonPath("$.detail").value("Restaurant not found: " + restaurantId));
  }

  @Test
  void post_uniqueViolation_returns_409_problem() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    when(createReview.create(
            eq(SUBJECT), eq(new RestaurantId(restaurantId)), anyInt(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("dup"));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 4 }
                    """))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/conflict"));
  }

  @Test
  void put_validPayload_returns_200_with_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    Review updated = review(restaurantId, 3, "Changed my mind");
    when(updateReview.update(
            eq(SUBJECT), eq(new RestaurantId(restaurantId)), eq(3), eq("Changed my mind")))
        .thenReturn(updated);

    mockMvc
        .perform(
            put("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 3, "comment": "Changed my mind" }
                    """))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.rating").value(3))
        .andExpect(jsonPath("$.comment").value("Changed my mind"));
  }

  @Test
  void put_reviewNotFound_returns_404() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    when(updateReview.update(any(), any(), anyInt(), any()))
        .thenThrow(new ReviewNotFoundException(new ReviewId(UUID.randomUUID())));

    mockMvc
        .perform(
            put("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 3 }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void put_anonymous_returns_401() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/user/restaurants/{rid}/reviews", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 4 }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void post_forwardsIdempotencyKeyHeader() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    Review created = review(restaurantId, 5, null);
    when(createReview.create(any(), any(), anyInt(), any(), any())).thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/reviews", restaurantId)
                .with(userJwt())
                .header("Idempotency-Key", "KEY-ABC-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rating": 5 }
                    """))
        .andExpect(status().isCreated());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Optional<String>> keyCaptor = ArgumentCaptor.forClass(Optional.class);
    verify(createReview)
        .create(eq(SUBJECT), eq(new RestaurantId(restaurantId)), eq(5), any(), keyCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(keyCaptor.getValue()).contains("KEY-ABC-123");
  }
}
