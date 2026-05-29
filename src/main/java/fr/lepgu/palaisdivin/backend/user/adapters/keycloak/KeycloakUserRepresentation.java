package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import java.util.List;

public record KeycloakUserRepresentation(
    String username,
    String email,
    boolean enabled,
    boolean emailVerified,
    String firstName,
    String lastName,
    List<Credential> credentials) {

  public record Credential(String type, String value, boolean temporary) {}
}
