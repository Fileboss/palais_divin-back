package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_connection")
class UserConnectionEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "source_user_id", nullable = false)
  private UUID sourceUserId;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserConnectionEntity() {}

  UserConnectionEntity(UUID id, UUID sourceUserId, UUID targetUserId, Instant createdAt) {
    this.id = id;
    this.sourceUserId = sourceUserId;
    this.targetUserId = targetUserId;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getSourceUserId() {
    return sourceUserId;
  }

  UUID getTargetUserId() {
    return targetUserId;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
