package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RestaurantId(UUID value) {
  public RestaurantId {
    Objects.requireNonNull(value, "value");
  }

  public static RestaurantId newId() {
    return new RestaurantId(UUID.randomUUID());
  }
}
