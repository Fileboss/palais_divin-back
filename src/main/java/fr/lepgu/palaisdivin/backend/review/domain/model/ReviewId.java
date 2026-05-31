package fr.lepgu.palaisdivin.backend.review.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ReviewId(UUID value) {
  public ReviewId {
    Objects.requireNonNull(value, "value");
  }

  public static ReviewId newId() {
    return new ReviewId(UUID.randomUUID());
  }
}
