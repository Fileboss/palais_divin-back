package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import java.time.Instant;
import java.util.UUID;

public record TagResponse(
    UUID id, TagCategory category, String slug, String label, Instant createdAt) {

  public static TagResponse from(Tag t) {
    return new TagResponse(t.id().value(), t.category(), t.slug(), t.label(), t.createdAt());
  }
}
