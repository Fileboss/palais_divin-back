package fr.lepgu.palaisdivin.backend.tag.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Tag(TagId id, TagCategory category, String slug, String label, Instant createdAt) {
  public Tag {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(slug, "slug");
    if (slug.isBlank()) {
      throw new IllegalArgumentException("slug must not be blank");
    }
    Objects.requireNonNull(label, "label");
    if (label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
