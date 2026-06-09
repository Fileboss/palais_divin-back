package fr.lepgu.palaisdivin.backend.tag.domain.events;

import java.time.Instant;
import java.util.UUID;

public record TagImplicationCreated(UUID tagId, UUID impliesTagId, Instant createdAt) {}
