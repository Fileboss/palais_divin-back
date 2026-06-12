package fr.lepgu.palaisdivin.backend.restaurant.domain;

public final class GeocodeFailedException extends RuntimeException {

  public GeocodeFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
