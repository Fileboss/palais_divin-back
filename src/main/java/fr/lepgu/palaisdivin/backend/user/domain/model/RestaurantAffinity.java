package fr.lepgu.palaisdivin.backend.user.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.util.Objects;

public record RestaurantAffinity(RestaurantId restaurantId, double affinity, int recommenderCount) {
  public RestaurantAffinity {
    Objects.requireNonNull(restaurantId, "restaurantId");
    if (recommenderCount < 0) {
      throw new IllegalArgumentException("recommenderCount must be >= 0");
    }
  }
}
