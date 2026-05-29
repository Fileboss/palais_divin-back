package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Invitation(
    InvitationId id,
    InvitationToken token,
    Instant expiresAt,
    Instant consumedAt,
    Instant createdAt) {
  public Invitation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(createdAt, "createdAt");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }
  }
}
