package fr.lepgu.palaisdivin.backend.restaurant.domain.events;

import java.time.Instant;
import java.util.UUID;

public record RestaurantDeleted(UUID id, Instant deletedAt) {}
