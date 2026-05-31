package fr.lepgu.palaisdivin.backend.review.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.Objects;

public record Review(
    ReviewId id,
    RestaurantId restaurantId,
    UserId authorId,
    int rating,
    String comment,
    Instant createdAt) {
  public Review {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(restaurantId, "restaurantId");
    Objects.requireNonNull(authorId, "authorId");
    if (rating < 1 || rating > 5) {
      throw new IllegalArgumentException("rating must be in [1, 5], got " + rating);
    }
    if (comment != null && comment.isBlank()) {
      throw new IllegalArgumentException("comment must not be blank when present");
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
