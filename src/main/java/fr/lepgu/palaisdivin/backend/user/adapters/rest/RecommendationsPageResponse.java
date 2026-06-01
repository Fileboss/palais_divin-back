package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import java.util.List;

public record RecommendationsPageResponse(List<RecommendationResponse> data, PageMeta page) {}
