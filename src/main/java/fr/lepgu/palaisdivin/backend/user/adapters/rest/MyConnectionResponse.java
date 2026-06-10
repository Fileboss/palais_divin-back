package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import java.time.Instant;

public record MyConnectionResponse(PublicUserResponse user, Instant createdAt) {}
