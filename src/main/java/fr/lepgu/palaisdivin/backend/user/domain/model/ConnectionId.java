package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ConnectionId(UUID value) {
  public ConnectionId {
    Objects.requireNonNull(value, "value");
  }

  public static ConnectionId newId() {
    return new ConnectionId(UUID.randomUUID());
  }
}
