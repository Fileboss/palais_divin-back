package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tag_implication")
@IdClass(TagImplicationPk.class)
class TagImplicationEntity {

  @Id
  @Column(name = "tag_id")
  private UUID tagId;

  @Id
  @Column(name = "implies_tag_id")
  private UUID impliesTagId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TagImplicationEntity() {}

  TagImplicationEntity(UUID tagId, UUID impliesTagId, Instant createdAt) {
    this.tagId = tagId;
    this.impliesTagId = impliesTagId;
    this.createdAt = createdAt;
  }

  UUID getTagId() {
    return tagId;
  }

  UUID getImpliesTagId() {
    return impliesTagId;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
