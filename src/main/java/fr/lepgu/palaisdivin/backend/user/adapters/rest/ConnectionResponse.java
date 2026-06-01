package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(UUID id, UUID sourceUserId, UUID targetUserId, Instant createdAt) {

  public static ConnectionResponse from(Connection c) {
    return new ConnectionResponse(
        c.id().value(), c.sourceUserId().value(), c.targetUserId().value(), c.createdAt());
  }
}
