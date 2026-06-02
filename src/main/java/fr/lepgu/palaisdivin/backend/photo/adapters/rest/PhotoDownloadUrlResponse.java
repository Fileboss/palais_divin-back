package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoDownloadUrl;
import java.time.Instant;

public record PhotoDownloadUrlResponse(String objectKey, String downloadUrl, Instant expiresAt) {

  public static PhotoDownloadUrlResponse from(PhotoDownloadUrl model) {
    return new PhotoDownloadUrlResponse(
        model.objectKey(), model.downloadUrl().toString(), model.expiresAt());
  }
}
