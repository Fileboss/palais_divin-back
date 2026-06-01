package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import java.util.List;

public record ReviewsPageResponse(List<ReviewResponse> data, PageMeta page) {}
