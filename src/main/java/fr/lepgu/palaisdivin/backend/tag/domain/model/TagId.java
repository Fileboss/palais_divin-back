package fr.lepgu.palaisdivin.backend.tag.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TagId(UUID value) {
  public TagId {
    Objects.requireNonNull(value, "value");
  }

  public static TagId newId() {
    return new TagId(UUID.randomUUID());
  }
}
