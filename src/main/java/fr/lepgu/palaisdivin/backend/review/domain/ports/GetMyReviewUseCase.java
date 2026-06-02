package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;

public interface GetMyReviewUseCase {

  Review getMyReview(String authorSubject, RestaurantId restaurantId);
}
