package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.util.Objects;

public record KeycloakUserId(String value) {
  public KeycloakUserId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
