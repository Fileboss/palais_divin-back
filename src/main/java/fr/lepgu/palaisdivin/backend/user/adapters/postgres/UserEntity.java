package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
class UserEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "subject", nullable = false, unique = true)
  private String subject;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserEntity() {}

  UserEntity(UUID id, String subject, String email, String displayName, Instant createdAt) {
    this.id = id;
    this.subject = subject;
    this.email = email;
    this.displayName = displayName;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getSubject() {
    return subject;
  }

  String getEmail() {
    return email;
  }

  String getDisplayName() {
    return displayName;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
