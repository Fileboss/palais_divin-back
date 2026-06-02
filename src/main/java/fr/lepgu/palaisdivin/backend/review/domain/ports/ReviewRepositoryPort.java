package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Optional;

public interface ReviewRepositoryPort {

  Review save(Review review);

  Optional<Review> findById(ReviewId id);

  Optional<Review> findByRestaurantAndAuthor(RestaurantId restaurantId, UserId authorId);

  CursorPage<Review> findByRestaurant(RestaurantId restaurantId, ReviewCursor cursor, int size);
}
