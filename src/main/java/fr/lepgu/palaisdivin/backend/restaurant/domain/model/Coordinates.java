package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

public record Coordinates(double latitude, double longitude) {
  public Coordinates {
    if (latitude < -90.0 || latitude > 90.0) {
      throw new IllegalArgumentException("latitude must be in [-90, 90], got " + latitude);
    }
    if (longitude < -180.0 || longitude > 180.0) {
      throw new IllegalArgumentException("longitude must be in [-180, 180], got " + longitude);
    }
  }
}
