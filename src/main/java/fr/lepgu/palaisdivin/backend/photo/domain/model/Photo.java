package fr.lepgu.palaisdivin.backend.photo.domain.model;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.Objects;

public record Photo(
    PhotoId id,
    RestaurantId restaurantId,
    UserId authorId,
    String objectKey,
    String contentType,
    Instant createdAt) {
  public Photo {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(restaurantId, "restaurantId");
    Objects.requireNonNull(authorId, "authorId");
    Objects.requireNonNull(objectKey, "objectKey");
    if (objectKey.isBlank()) {
      throw new IllegalArgumentException("objectKey must not be blank");
    }
    Objects.requireNonNull(contentType, "contentType");
    if (contentType.isBlank()) {
      throw new IllegalArgumentException("contentType must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
