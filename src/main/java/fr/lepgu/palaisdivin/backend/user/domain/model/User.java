package fr.lepgu.palaisdivin.backend.user.domain.model;

import java.time.Instant;
import java.util.Objects;

public record User(UserId id, String subject, String email, String displayName, Instant createdAt) {
  public User {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(subject, "subject");
    if (subject.isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }
    Objects.requireNonNull(email, "email");
    if (email.isBlank()) {
      throw new IllegalArgumentException("email must not be blank");
    }
    Objects.requireNonNull(displayName, "displayName");
    if (displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
