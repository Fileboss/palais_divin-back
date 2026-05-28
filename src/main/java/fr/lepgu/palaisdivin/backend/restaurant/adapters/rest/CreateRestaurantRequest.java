package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateRestaurantRequest(@NotBlank String name, @NotBlank String address) {}
