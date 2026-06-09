package fr.lepgu.palaisdivin.backend.tag.domain.events;

import java.time.Instant;
import java.util.UUID;

public record TagImplicationDeleted(UUID tagId, UUID impliesTagId, Instant deletedAt) {}
