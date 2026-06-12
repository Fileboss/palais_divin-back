package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.CoordinatesDto;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import java.util.UUID;

public record RecommendationResponse(
    UUID id,
    String name,
    String address,
    CoordinatesDto location,
    double affinity,
    int recommenderCount,
    Double avgRating,
    Double distanceMetres) {

  public static RecommendationResponse from(Recommendation r) {
    return new RecommendationResponse(
        r.restaurantId().value(),
        r.name(),
        r.address(),
        new CoordinatesDto(r.latitude(), r.longitude()),
        r.affinity(),
        r.recommenderCount(),
        r.avgRating(),
        r.distanceMetres());
  }
}
