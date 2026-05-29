package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InvitationId(UUID value) {
  public InvitationId {
    Objects.requireNonNull(value, "value");
  }

  public static InvitationId newId() {
    return new InvitationId(UUID.randomUUID());
  }
}
