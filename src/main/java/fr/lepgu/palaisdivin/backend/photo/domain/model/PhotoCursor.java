package fr.lepgu.palaisdivin.backend.photo.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PhotoCursor(Instant createdAt, UUID id) {
  public PhotoCursor {
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(id, "id");
  }
}
