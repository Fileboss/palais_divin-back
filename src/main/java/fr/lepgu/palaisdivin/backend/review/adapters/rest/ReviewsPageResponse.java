package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import java.util.List;

public record ReviewsPageResponse(List<ReviewResponse> data, PageMeta page) {}
