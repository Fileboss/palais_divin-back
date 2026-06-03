package fr.lepgu.palaisdivin.backend.tag.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.Objects;

public record RestaurantTag(
    RestaurantId restaurantId, TagId tagId, UserId attachedBy, Instant attachedAt) {
  public RestaurantTag {
    Objects.requireNonNull(restaurantId, "restaurantId");
    Objects.requireNonNull(tagId, "tagId");
    Objects.requireNonNull(attachedBy, "attachedBy");
    Objects.requireNonNull(attachedAt, "attachedAt");
  }
}
