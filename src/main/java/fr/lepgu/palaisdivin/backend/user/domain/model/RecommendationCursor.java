package fr.lepgu.palaisdivin.backend.user.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public sealed interface RecommendationCursor
    permits RecommendationCursor.ByAffinity,
        RecommendationCursor.ByRating,
        RecommendationCursor.ByName,
        RecommendationCursor.ByDistance,
        RecommendationCursor.ByCreatedAt {

  RestaurantId id();

  record ByAffinity(double affinity, RestaurantId id) implements RecommendationCursor {
    public ByAffinity {
      Objects.requireNonNull(id, "id");
    }
  }

  record ByRating(BigDecimal avgRating, RestaurantId id) implements RecommendationCursor {
    public ByRating {
      Objects.requireNonNull(id, "id");
    }
  }

  record ByName(String name, RestaurantId id) implements RecommendationCursor {
    public ByName {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(id, "id");
    }
  }

  record ByDistance(double distanceMetres, RestaurantId id) implements RecommendationCursor {
    public ByDistance {
      Objects.requireNonNull(id, "id");
    }
  }

  record ByCreatedAt(Instant createdAt, RestaurantId id) implements RecommendationCursor {
    public ByCreatedAt {
      Objects.requireNonNull(createdAt, "createdAt");
      Objects.requireNonNull(id, "id");
    }
  }
}
