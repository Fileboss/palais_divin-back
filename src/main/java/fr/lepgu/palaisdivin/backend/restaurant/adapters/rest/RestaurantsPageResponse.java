package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import java.util.List;

public record RestaurantsPageResponse(List<RestaurantResponse> data, PageMeta page) {}
