package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InvitationToken(String value) {
  public InvitationToken {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  public static InvitationToken newToken() {
    return new InvitationToken(UUID.randomUUID().toString());
  }
}
