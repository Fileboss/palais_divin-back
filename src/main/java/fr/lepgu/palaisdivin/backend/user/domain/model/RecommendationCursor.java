package fr.lepgu.palaisdivin.backend.user.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.util.Objects;

public record RecommendationCursor(double affinity, RestaurantId id) {
  public RecommendationCursor {
    Objects.requireNonNull(id, "id");
  }
}
