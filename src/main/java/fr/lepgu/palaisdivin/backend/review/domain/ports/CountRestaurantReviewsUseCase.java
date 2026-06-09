package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;

public interface CountRestaurantReviewsUseCase {

  long countByRestaurant(RestaurantId restaurantId);
}
