package fr.lepgu.palaisdivin.backend.tag.domain.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RestaurantTagAttached(
    UUID restaurantId,
    UUID tagId,
    String tagSlug,
    String tagCategory,
    String tagLabel,
    Map<String, String> tagLabelI18n,
    UUID attachedBy,
    Instant attachedAt) {}
