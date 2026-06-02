package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import java.time.Instant;
import java.util.UUID;

public record PhotoResponse(
    UUID id,
    UUID restaurantId,
    UUID authorId,
    String objectKey,
    String contentType,
    Instant createdAt) {

  public static PhotoResponse from(Photo p) {
    return new PhotoResponse(
        p.id().value(),
        p.restaurantId().value(),
        p.authorId().value(),
        p.objectKey(),
        p.contentType(),
        p.createdAt());
  }
}
