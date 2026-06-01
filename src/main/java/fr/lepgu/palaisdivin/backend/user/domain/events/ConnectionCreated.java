package fr.lepgu.palaisdivin.backend.user.domain.events;

import java.time.Instant;
import java.util.UUID;

public record ConnectionCreated(UUID id, UUID sourceUserId, UUID targetUserId, Instant createdAt) {}
