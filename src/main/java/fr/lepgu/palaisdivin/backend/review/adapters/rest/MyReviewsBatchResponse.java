package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

public record MyReviewsBatchResponse(
    @JsonInclude(JsonInclude.Include.ALWAYS) Map<UUID, ReviewResponse> reviews) {}
