package fr.lepgu.palaisdivin.backend.review.adapters.postgres;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ReviewRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewPostgresAdapter implements ReviewRepositoryPort {

  private final ReviewJpaRepository jpa;

  ReviewPostgresAdapter(ReviewJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Review save(Review review) {
    return toDomain(jpa.save(toEntity(review)));
  }

  @Override
  public Optional<Review> findById(ReviewId id) {
    return jpa.findById(id.value()).map(ReviewPostgresAdapter::toDomain);
  }

  private static ReviewEntity toEntity(Review r) {
    return new ReviewEntity(
        r.id().value(),
        r.restaurantId().value(),
        r.authorId().value(),
        r.rating(),
        r.comment(),
        r.createdAt());
  }

  private static Review toDomain(ReviewEntity e) {
    return new Review(
        new ReviewId(e.getId()),
        new RestaurantId(e.getRestaurantId()),
        new UserId(e.getAuthorId()),
        e.getRating(),
        e.getComment(),
        e.getCreatedAt());
  }
}
