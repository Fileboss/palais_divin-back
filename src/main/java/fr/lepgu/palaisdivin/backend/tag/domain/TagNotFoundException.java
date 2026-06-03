package fr.lepgu.palaisdivin.backend.tag.domain;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;

public final class TagNotFoundException extends RuntimeException {

  public TagNotFoundException(TagId id) {
    super("Tag not found: " + id.value());
  }
}
