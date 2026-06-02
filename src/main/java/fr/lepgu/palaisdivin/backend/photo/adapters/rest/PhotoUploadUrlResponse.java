package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import java.time.Instant;

public record PhotoUploadUrlResponse(String objectKey, String uploadUrl, Instant expiresAt) {

  public static PhotoUploadUrlResponse from(PhotoUploadUrl model) {
    return new PhotoUploadUrlResponse(
        model.objectKey(), model.uploadUrl().toString(), model.expiresAt());
  }
}
