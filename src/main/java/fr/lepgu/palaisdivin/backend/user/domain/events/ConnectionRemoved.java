package fr.lepgu.palaisdivin.backend.user.domain.events;

import java.time.Instant;
import java.util.UUID;

public record ConnectionRemoved(UUID id, UUID sourceUserId, UUID targetUserId, Instant removedAt) {}
