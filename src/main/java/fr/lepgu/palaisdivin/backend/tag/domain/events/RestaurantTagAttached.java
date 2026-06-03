package fr.lepgu.palaisdivin.backend.tag.domain.events;

import java.time.Instant;
import java.util.UUID;

public record RestaurantTagAttached(
    UUID restaurantId,
    UUID tagId,
    String tagSlug,
    String tagCategory,
    String tagLabel,
    UUID attachedBy,
    Instant attachedAt) {}
