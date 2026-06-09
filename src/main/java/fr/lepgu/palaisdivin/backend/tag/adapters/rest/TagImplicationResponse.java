package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import java.time.Instant;
import java.util.UUID;

public record TagImplicationResponse(UUID tagId, UUID impliesTagId, Instant createdAt) {
  public static TagImplicationResponse from(TagImplication i) {
    return new TagImplicationResponse(i.tagId().value(), i.impliesTagId().value(), i.createdAt());
  }
}
