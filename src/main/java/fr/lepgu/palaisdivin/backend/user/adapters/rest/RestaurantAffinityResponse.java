package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import java.util.UUID;

public record RestaurantAffinityResponse(UUID restaurantId, double affinity, int recommenderCount) {

  public static RestaurantAffinityResponse from(RestaurantAffinity a) {
    return new RestaurantAffinityResponse(
        a.restaurantId().value(), a.affinity(), a.recommenderCount());
  }
}
