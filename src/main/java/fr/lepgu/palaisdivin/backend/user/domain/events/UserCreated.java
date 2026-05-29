package fr.lepgu.palaisdivin.backend.user.domain.events;

import java.time.Instant;
import java.util.UUID;

public record UserCreated(
    UUID id, String subject, String email, String displayName, Instant createdAt) {}
