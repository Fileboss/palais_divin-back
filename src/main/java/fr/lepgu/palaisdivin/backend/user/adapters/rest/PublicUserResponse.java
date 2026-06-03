package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record PublicUserResponse(UUID id, String displayName, Instant createdAt) {

  public static PublicUserResponse from(User u) {
    return new PublicUserResponse(u.id().value(), u.displayName(), u.createdAt());
  }
}
