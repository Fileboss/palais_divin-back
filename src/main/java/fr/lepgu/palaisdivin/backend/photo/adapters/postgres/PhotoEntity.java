package fr.lepgu.palaisdivin.backend.photo.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photo")
class PhotoEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "restaurant_id", nullable = false)
  private UUID restaurantId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PhotoEntity() {}

  PhotoEntity(
      UUID id,
      UUID restaurantId,
      UUID authorId,
      String objectKey,
      String contentType,
      Instant createdAt) {
    this.id = id;
    this.restaurantId = restaurantId;
    this.authorId = authorId;
    this.objectKey = objectKey;
    this.contentType = contentType;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getRestaurantId() {
    return restaurantId;
  }

  UUID getAuthorId() {
    return authorId;
  }

  String getObjectKey() {
    return objectKey;
  }

  String getContentType() {
    return contentType;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
