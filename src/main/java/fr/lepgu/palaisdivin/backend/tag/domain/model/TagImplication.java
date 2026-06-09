package fr.lepgu.palaisdivin.backend.tag.domain.model;

import java.time.Instant;
import java.util.Objects;

public record TagImplication(TagId tagId, TagId impliesTagId, Instant createdAt) {
  public TagImplication {
    Objects.requireNonNull(tagId, "tagId");
    Objects.requireNonNull(impliesTagId, "impliesTagId");
    Objects.requireNonNull(createdAt, "createdAt");
    if (tagId.equals(impliesTagId)) {
      throw new IllegalArgumentException("tagId must differ from impliesTagId");
    }
  }
}
