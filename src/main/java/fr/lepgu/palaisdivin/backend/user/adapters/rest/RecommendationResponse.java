package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import java.util.UUID;

public record RecommendationResponse(
    UUID id,
    String name,
    String address,
    double latitude,
    double longitude,
    double affinity,
    int recommenderCount) {

  public static RecommendationResponse from(Recommendation r) {
    return new RecommendationResponse(
        r.restaurantId().value(),
        r.name(),
        r.address(),
        r.latitude(),
        r.longitude(),
        r.affinity(),
        r.recommenderCount());
  }
}
