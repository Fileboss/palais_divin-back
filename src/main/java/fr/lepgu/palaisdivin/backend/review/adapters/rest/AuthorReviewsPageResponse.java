package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import java.util.List;

public record AuthorReviewsPageResponse(List<AuthorReviewResponse> data, PageMeta page) {}
