package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_tag")
@IdClass(RestaurantTagPk.class)
class RestaurantTagEntity {

  @Id
  @Column(name = "restaurant_id")
  private UUID restaurantId;

  @Id
  @Column(name = "tag_id")
  private UUID tagId;

  @Column(name = "attached_by", nullable = false)
  private UUID attachedBy;

  @Column(name = "attached_at", nullable = false)
  private Instant attachedAt;

  protected RestaurantTagEntity() {}

  RestaurantTagEntity(UUID restaurantId, UUID tagId, UUID attachedBy, Instant attachedAt) {
    this.restaurantId = restaurantId;
    this.tagId = tagId;
    this.attachedBy = attachedBy;
    this.attachedAt = attachedAt;
  }

  UUID getRestaurantId() {
    return restaurantId;
  }

  UUID getTagId() {
    return tagId;
  }

  UUID getAttachedBy() {
    return attachedBy;
  }

  Instant getAttachedAt() {
    return attachedAt;
  }
}
