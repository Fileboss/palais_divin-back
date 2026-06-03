package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import java.time.Instant;
import java.util.UUID;

public record RestaurantTagResponse(
    UUID restaurantId, UUID tagId, UUID attachedBy, Instant attachedAt) {

  public static RestaurantTagResponse from(RestaurantTag a) {
    return new RestaurantTagResponse(
        a.restaurantId().value(), a.tagId().value(), a.attachedBy().value(), a.attachedAt());
  }
}
