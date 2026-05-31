package fr.lepgu.palaisdivin.backend.review.domain.ports;

import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import java.util.Optional;

public interface ReviewRepositoryPort {

  Review save(Review review);

  Optional<Review> findById(ReviewId id);
}
