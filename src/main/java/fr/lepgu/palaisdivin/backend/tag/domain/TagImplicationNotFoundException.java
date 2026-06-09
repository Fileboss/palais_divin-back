package fr.lepgu.palaisdivin.backend.tag.domain;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;

public final class TagImplicationNotFoundException extends RuntimeException {

  private final TagId tagId;
  private final TagId impliesTagId;

  public TagImplicationNotFoundException(TagId tagId, TagId impliesTagId) {
    super("Tag implication not found: " + tagId.value() + " -> " + impliesTagId.value());
    this.tagId = tagId;
    this.impliesTagId = impliesTagId;
  }

  public TagId tagId() {
    return tagId;
  }

  public TagId impliesTagId() {
    return impliesTagId;
  }
}
