package fr.lepgu.palaisdivin.backend.photo.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PhotoId(UUID value) {
  public PhotoId {
    Objects.requireNonNull(value, "value");
  }

  public static PhotoId newId() {
    return new PhotoId(UUID.randomUUID());
  }
}
