package fr.lepgu.palaisdivin.backend.review.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review")
class ReviewEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "restaurant_id", nullable = false)
  private UUID restaurantId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "rating", nullable = false)
  private int rating;

  @Column(name = "comment")
  private String comment;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ReviewEntity() {}

  ReviewEntity(
      UUID id, UUID restaurantId, UUID authorId, int rating, String comment, Instant createdAt) {
    this.id = id;
    this.restaurantId = restaurantId;
    this.authorId = authorId;
    this.rating = rating;
    this.comment = comment;
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

  int getRating() {
    return rating;
  }

  String getComment() {
    return comment;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
