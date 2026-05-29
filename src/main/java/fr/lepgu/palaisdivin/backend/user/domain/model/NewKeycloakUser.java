package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.util.List;
import java.util.Objects;

public record NewKeycloakUser(
    String username,
    String email,
    String displayName,
    String temporaryPassword,
    List<String> realmRoles) {

  public NewKeycloakUser {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(temporaryPassword, "temporaryPassword");
    Objects.requireNonNull(realmRoles, "realmRoles");
    realmRoles = List.copyOf(realmRoles);
    if (realmRoles.isEmpty()) {
      throw new IllegalArgumentException("realmRoles must not be empty");
    }
  }
}
