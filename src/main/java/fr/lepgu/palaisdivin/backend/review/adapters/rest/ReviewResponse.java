package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID restaurantId,
    UUID authorId,
    String authorDisplayName,
    int rating,
    String comment,
    Instant createdAt) {

  public static ReviewResponse from(Review r) {
    return from(r, null);
  }

  public static ReviewResponse from(Review r, String authorDisplayName) {
    return new ReviewResponse(
        r.id().value(),
        r.restaurantId().value(),
        r.authorId().value(),
        authorDisplayName,
        r.rating(),
        r.comment(),
        r.createdAt());
  }
}
