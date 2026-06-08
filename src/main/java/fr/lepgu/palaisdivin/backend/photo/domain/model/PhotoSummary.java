package fr.lepgu.palaisdivin.backend.photo.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record PhotoSummary(PhotoId id, URI url, Instant expiresAt) {
  public PhotoSummary {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }
}
