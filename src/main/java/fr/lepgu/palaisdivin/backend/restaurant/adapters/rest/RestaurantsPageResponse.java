package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import java.util.List;

public record RestaurantsPageResponse(List<RestaurantResponse> data, PageMeta page) {}
