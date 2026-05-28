package fr.lepgu.palaisdivin.backend.restaurant.domain.events;

import java.time.Instant;
import java.util.UUID;

public record RestaurantCreated(
    UUID id, String name, String address, double latitude, double longitude, Instant createdAt) {}
