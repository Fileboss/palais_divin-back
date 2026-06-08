package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummary;
import java.time.Instant;
import java.util.UUID;

public record PhotoSummaryResponse(UUID id, String url, Instant expiresAt) {

  public static PhotoSummaryResponse from(PhotoSummary summary) {
    return new PhotoSummaryResponse(
        summary.id().value(), summary.url().toString(), summary.expiresAt());
  }
}
