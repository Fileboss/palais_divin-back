package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation")
class InvitationEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "token", nullable = false, unique = true)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected InvitationEntity() {}

  InvitationEntity(
      UUID id, String token, Instant expiresAt, Instant consumedAt, Instant createdAt) {
    this.id = id;
    this.token = token;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getToken() {
    return token;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  Instant getConsumedAt() {
    return consumedAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
