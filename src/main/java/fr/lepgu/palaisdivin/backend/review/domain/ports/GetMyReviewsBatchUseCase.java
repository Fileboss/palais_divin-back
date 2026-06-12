package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface GetMyReviewsBatchUseCase {

  Map<RestaurantId, Optional<Review>> getMyReviewsBatch(
      String authorSubject, Collection<RestaurantId> restaurantIds);
}
