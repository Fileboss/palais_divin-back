package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "label_i18n", columnDefinition = "jsonb")
  private Map<String, String> labelI18n;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TagEntity() {}

  TagEntity(
      UUID id,
      String category,
      String slug,
      String label,
      Map<String, String> labelI18n,
      Instant createdAt) {
    this.id = id;
    this.category = category;
    this.slug = slug;
    this.label = label;
    this.labelI18n = labelI18n;
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

  Map<String, String> getLabelI18n() {
    return labelI18n;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
