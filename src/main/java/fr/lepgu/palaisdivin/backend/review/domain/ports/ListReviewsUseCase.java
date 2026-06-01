package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;

public interface ListReviewsUseCase {

  CursorPage<Review> listByRestaurant(RestaurantId restaurantId, ReviewCursor cursor, int size);
}
