package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id, UUID restaurantId, UUID authorId, int rating, String comment, Instant createdAt) {

  public static ReviewResponse from(Review r) {
    return new ReviewResponse(
        r.id().value(),
        r.restaurantId().value(),
        r.authorId().value(),
        r.rating(),
        r.comment(),
        r.createdAt());
  }
}
