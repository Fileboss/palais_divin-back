package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
class OutboxEventEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  private JsonNode payload;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected OutboxEventEntity() {}

  OutboxEventEntity(
      UUID id,
      String aggregateType,
      UUID aggregateId,
      String eventType,
      JsonNode payload,
      Instant createdAt) {
    this.id = id;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = "PENDING";
    this.retryCount = 0;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getAggregateType() {
    return aggregateType;
  }

  UUID getAggregateId() {
    return aggregateId;
  }

  String getEventType() {
    return eventType;
  }

  JsonNode getPayload() {
    return payload;
  }

  String getStatus() {
    return status;
  }

  int getRetryCount() {
    return retryCount;
  }

  String getLastError() {
    return lastError;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getProcessedAt() {
    return processedAt;
  }

  void markProcessed(Instant when) {
    this.status = "PROCESSED";
    this.processedAt = when;
    this.lastError = null;
  }

  void markFailed(String error) {
    this.status = "FAILED";
    this.lastError = error;
  }

  void incrementRetry() {
    this.retryCount++;
  }

  void recordError(String error) {
    this.lastError = error;
  }
}
