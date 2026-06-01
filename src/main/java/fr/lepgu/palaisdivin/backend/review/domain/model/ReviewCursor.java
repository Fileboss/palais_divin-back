package fr.lepgu.palaisdivin.backend.review.domain.model;

import java.time.Instant;
import java.util.Objects;

public record ReviewCursor(Instant createdAt, ReviewId id) {
  public ReviewCursor {
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(id, "id");
  }
}
