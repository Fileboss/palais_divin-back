package fr.lepgu.palaisdivin.backend.user.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.time.Instant;
import java.util.Objects;

public record Recommendation(
    RestaurantId restaurantId,
    String name,
    String address,
    double latitude,
    double longitude,
    double affinity,
    int recommenderCount,
    Double avgRating,
    Double distanceMetres,
    Instant createdAt) {
  public Recommendation {
    Objects.requireNonNull(restaurantId, "restaurantId");
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    Objects.requireNonNull(address, "address");
    if (address.isBlank()) {
      throw new IllegalArgumentException("address must not be blank");
    }
    if (recommenderCount < 1) {
      throw new IllegalArgumentException("recommenderCount must be >= 1");
    }
  }

  public Recommendation(
      RestaurantId restaurantId,
      String name,
      String address,
      double latitude,
      double longitude,
      double affinity,
      int recommenderCount) {
    this(
        restaurantId,
        name,
        address,
        latitude,
        longitude,
        affinity,
        recommenderCount,
        null,
        null,
        null);
  }
}
