package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record SignupResponse(UUID id, String email, String displayName, Instant createdAt) {

  public static SignupResponse from(User user) {
    return new SignupResponse(
        user.id().value(), user.email(), user.displayName(), user.createdAt());
  }
}
