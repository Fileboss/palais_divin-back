package fr.lepgu.palaisdivin.backend.tag.domain.events;

import java.time.Instant;
import java.util.UUID;

public record RestaurantTagDetached(UUID restaurantId, UUID tagId, Instant detachedAt) {}
