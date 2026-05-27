package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import java.time.Instant;
import java.util.UUID;

public record RestaurantResponse(
    UUID id, String name, String address, CoordinatesDto location, Instant createdAt) {

  public static RestaurantResponse from(Restaurant r) {
    return new RestaurantResponse(
        r.id().value(),
        r.name(),
        r.address(),
        new CoordinatesDto(r.location().latitude(), r.location().longitude()),
        r.createdAt());
  }
}
