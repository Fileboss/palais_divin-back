package fr.lepgu.palaisdivin.backend.user.domain;

public final class KeycloakOperationException extends RuntimeException {

  public KeycloakOperationException(String message) {
    super(message);
  }

  public KeycloakOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
