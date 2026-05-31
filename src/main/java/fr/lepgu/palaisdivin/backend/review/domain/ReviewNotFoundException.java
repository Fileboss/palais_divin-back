package fr.lepgu.palaisdivin.backend.review.domain;

import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;

public final class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(ReviewId id) {
    super("Review not found: " + id.value());
  }
}
