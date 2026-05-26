package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import java.time.Instant;

public record PingResponse(String status, Instant ts) {}
