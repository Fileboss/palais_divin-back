package fr.lepgu.palaisdivin.backend.photo.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record PhotoUploadUrl(String objectKey, URI uploadUrl, Instant expiresAt) {
  public PhotoUploadUrl {
    Objects.requireNonNull(objectKey, "objectKey");
    Objects.requireNonNull(uploadUrl, "uploadUrl");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }
}
