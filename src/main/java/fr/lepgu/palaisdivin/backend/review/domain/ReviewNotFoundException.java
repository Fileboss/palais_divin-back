package fr.lepgu.palaisdivin.backend.review.domain;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public final class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(ReviewId id) {
    super("Review not found: " + id.value());
  }

  public ReviewNotFoundException(RestaurantId restaurantId, UserId authorId) {
    super(
        "Review not found for restaurant "
            + restaurantId.value()
            + " and author "
            + authorId.value());
  }
}
