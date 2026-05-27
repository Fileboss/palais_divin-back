package fr.lepgu.palaisdivin.backend.restaurant.domain;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;

public final class RestaurantNotFoundException extends RuntimeException {

  public RestaurantNotFoundException(RestaurantId id) {
    super("Restaurant not found: " + id.value());
  }
}
