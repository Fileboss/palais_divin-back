package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

class TagImplicationPk implements Serializable {

  private UUID tagId;
  private UUID impliesTagId;

  protected TagImplicationPk() {}

  TagImplicationPk(UUID tagId, UUID impliesTagId) {
    this.tagId = tagId;
    this.impliesTagId = impliesTagId;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TagImplicationPk other)) {
      return false;
    }
    return Objects.equals(tagId, other.tagId) && Objects.equals(impliesTagId, other.impliesTagId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tagId, impliesTagId);
  }
}
