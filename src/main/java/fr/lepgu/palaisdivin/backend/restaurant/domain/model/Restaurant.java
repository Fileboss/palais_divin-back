package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Restaurant(
    RestaurantId id,
    String name,
    String address,
    Coordinates location,
    Instant createdAt,
    Double avgRating,
    Double distanceMetres,
    Double affinity) {
  public Restaurant {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  public Restaurant(
      RestaurantId id,
      String name,
      String address,
      Coordinates location,
      Instant createdAt,
      Double avgRating,
      Double distanceMetres) {
    this(id, name, address, location, createdAt, avgRating, distanceMetres, null);
  }

  public Restaurant(
      RestaurantId id,
      String name,
      String address,
      Coordinates location,
      Instant createdAt,
      Double avgRating) {
    this(id, name, address, location, createdAt, avgRating, null, null);
  }
}
