package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public interface FindReviewByAuthorUseCase {

  Review findByRestaurantAndAuthor(RestaurantId restaurantId, UserId authorId);
}
