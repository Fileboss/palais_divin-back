package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Connection(
    ConnectionId id, UserId sourceUserId, UserId targetUserId, Instant createdAt) {
  public Connection {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(sourceUserId, "sourceUserId");
    Objects.requireNonNull(targetUserId, "targetUserId");
    Objects.requireNonNull(createdAt, "createdAt");
    if (sourceUserId.equals(targetUserId)) {
      throw new IllegalArgumentException("connection cannot be a self-loop");
    }
  }
}
