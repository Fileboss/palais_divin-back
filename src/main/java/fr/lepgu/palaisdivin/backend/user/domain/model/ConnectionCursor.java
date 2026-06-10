package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.time.Instant;
import java.util.Objects;

public record ConnectionCursor(Instant createdAt, ConnectionId id) {
  public ConnectionCursor {
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(id, "id");
  }
}
