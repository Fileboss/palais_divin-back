package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
  public UserId {
    Objects.requireNonNull(value, "value");
  }

  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }
}
