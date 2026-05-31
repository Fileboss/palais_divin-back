package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import java.util.Optional;

public interface CreateReviewUseCase {

  Review create(
      String authorSubject,
      RestaurantId restaurantId,
      int rating,
      String comment,
      Optional<String> idempotencyKey);
}
