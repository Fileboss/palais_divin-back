package fr.lepgu.palaisdivin.backend.user.domain;

public final class KeycloakOperationException extends RuntimeException {

  private final Integer statusCode;

  public KeycloakOperationException(String message) {
    super(message);
    this.statusCode = null;
  }

  public KeycloakOperationException(String message, Throwable cause) {
    super(message, cause);
    this.statusCode = null;
  }

  public KeycloakOperationException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public Integer statusCode() {
    return statusCode;
  }
}
