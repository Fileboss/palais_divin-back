package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReviewResponseTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Test
  void fromReview_unEnriched_setsAuthorDisplayNameNull() {
    Review review = sampleReview();

    ReviewResponse response = ReviewResponse.from(review);

    assertThat(response.id()).isEqualTo(review.id().value());
    assertThat(response.restaurantId()).isEqualTo(review.restaurantId().value());
    assertThat(response.authorId()).isEqualTo(review.authorId().value());
    assertThat(response.authorDisplayName()).isNull();
    assertThat(response.rating()).isEqualTo(review.rating());
    assertThat(response.comment()).isEqualTo(review.comment());
    assertThat(response.createdAt()).isEqualTo(review.createdAt());
  }

  @Test
  void fromReviewWithDisplayName_setsAuthorDisplayName() {
    Review review = sampleReview();

    ReviewResponse response = ReviewResponse.from(review, "Alice");

    assertThat(response.authorDisplayName()).isEqualTo("Alice");
    assertThat(response.authorId()).isEqualTo(review.authorId().value());
  }

  @Test
  void fromReviewWithNullDisplayName_keepsNull() {
    Review review = sampleReview();

    ReviewResponse response = ReviewResponse.from(review, null);

    assertThat(response.authorDisplayName()).isNull();
  }

  private static Review sampleReview() {
    return new Review(
        ReviewId.newId(), RestaurantId.newId(), UserId.newId(), 4, "Nice place", FIXED_CREATED_AT);
  }
}
