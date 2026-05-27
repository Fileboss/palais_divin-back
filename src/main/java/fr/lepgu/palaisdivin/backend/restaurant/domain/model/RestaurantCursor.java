package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RestaurantCursor(Instant createdAt, UUID id) {
  public RestaurantCursor {
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(id, "id");
  }
}
