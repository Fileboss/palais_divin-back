package fr.lepgu.palaisdivin.backend.review.domain.events;

import java.time.Instant;
import java.util.UUID;

public record ReviewCreated(
    UUID id, UUID restaurantId, UUID authorId, int rating, String comment, Instant createdAt) {}
