package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tag")
class TagEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "category", nullable = false, length = 32)
  private String category;

  @Column(name = "slug", nullable = false, length = 64)
  private String slug;

  @Column(name = "label", nullable = false, length = 127)
  private String label;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TagEntity() {}

  TagEntity(UUID id, String category, String slug, String label, Instant createdAt) {
    this.id = id;
    this.category = category;
    this.slug = slug;
    this.label = label;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getCategory() {
    return category;
  }

  String getSlug() {
    return slug;
  }

  String getLabel() {
    return label;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
