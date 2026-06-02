package fr.lepgu.palaisdivin.backend.photo.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record PhotoDownloadUrl(String objectKey, URI downloadUrl, Instant expiresAt) {
  public PhotoDownloadUrl {
    Objects.requireNonNull(objectKey, "objectKey");
    Objects.requireNonNull(downloadUrl, "downloadUrl");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }
}
