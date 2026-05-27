package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRestaurantRequest(
    @NotBlank String name, String address, @NotNull @Valid CoordinatesDto location) {}
