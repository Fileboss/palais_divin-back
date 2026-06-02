package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;

public interface UpdateReviewUseCase {

  Review update(String authorSubject, RestaurantId restaurantId, int rating, String comment);
}
