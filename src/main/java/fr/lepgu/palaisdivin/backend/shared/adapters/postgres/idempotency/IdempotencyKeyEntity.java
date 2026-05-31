package fr.lepgu.palaisdivin.backend.shared.adapters.postgres.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_key")
class IdempotencyKeyEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "key", nullable = false, length = 255)
  private String key;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "aggregate_type", nullable = false, length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IdempotencyKeyEntity() {}

  IdempotencyKeyEntity(
      UUID id, String key, UUID userId, String aggregateType, UUID aggregateId, Instant createdAt) {
    this.id = id;
    this.key = key;
    this.userId = userId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getKey() {
    return key;
  }

  UUID getUserId() {
    return userId;
  }

  String getAggregateType() {
    return aggregateType;
  }

  UUID getAggregateId() {
    return aggregateId;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
