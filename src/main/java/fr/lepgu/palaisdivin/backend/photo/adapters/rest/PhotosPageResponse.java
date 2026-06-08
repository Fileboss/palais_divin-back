package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import java.util.List;

public record PhotosPageResponse(List<PhotoSummaryResponse> data, PageMeta page) {}
