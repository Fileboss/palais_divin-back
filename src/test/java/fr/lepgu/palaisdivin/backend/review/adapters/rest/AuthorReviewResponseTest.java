package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthorReviewResponseTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-06-04T10:00:00Z");

  @Test
  void from_mapsReviewAndNestedRestaurant() {
    ReviewId reviewId = ReviewId.newId();
    RestaurantId restaurantId = RestaurantId.newId();
    Review review =
        new Review(reviewId, restaurantId, UserId.newId(), 4, "Solid", FIXED_CREATED_AT);
    Restaurant restaurant =
        new Restaurant(
            restaurantId,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT.minusSeconds(3600),
            null);

    AuthorReviewResponse out = AuthorReviewResponse.from(review, restaurant);

    assertThat(out.reviewId()).isEqualTo(reviewId.value());
    assertThat(out.rating()).isEqualTo(4);
    assertThat(out.comment()).isEqualTo("Solid");
    assertThat(out.createdAt()).isEqualTo(FIXED_CREATED_AT);
    assertThat(out.restaurant().id()).isEqualTo(restaurantId.value());
    assertThat(out.restaurant().name()).isEqualTo("Septime");
    assertThat(out.restaurant().address()).isEqualTo("80 Rue de Charonne");
  }
}
