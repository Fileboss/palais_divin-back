package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import java.time.Instant;
import java.util.UUID;

public record AuthorReviewResponse(
    UUID reviewId,
    int rating,
    String comment,
    Instant createdAt,
    AuthorReviewRestaurantRef restaurant) {

  public static AuthorReviewResponse from(Review r, Restaurant rest) {
    return new AuthorReviewResponse(
        r.id().value(),
        r.rating(),
        r.comment(),
        r.createdAt(),
        new AuthorReviewRestaurantRef(rest.id().value(), rest.name(), rest.address()));
  }
}
