package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public sealed interface RestaurantCursor
    permits RestaurantCursor.ByCreatedAt, RestaurantCursor.ByRating, RestaurantCursor.ByName {

  UUID id();

  record ByCreatedAt(Instant createdAt, UUID id) implements RestaurantCursor {
    public ByCreatedAt {
      Objects.requireNonNull(createdAt, "createdAt");
      Objects.requireNonNull(id, "id");
    }
  }

  record ByRating(BigDecimal avgRating, UUID id) implements RestaurantCursor {
    public ByRating {
      Objects.requireNonNull(id, "id");
    }
  }

  record ByName(String name, UUID id) implements RestaurantCursor {
    public ByName {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(id, "id");
    }
  }
}
