package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateRestaurantRequest(
    @NotBlank String name,
    @NotBlank String address,
    Boolean dineIn,
    Boolean takeOut,
    Boolean delivery) {

  public CreateRestaurantRequest(String name, String address) {
    this(name, address, null, null, null);
  }

  public boolean dineInOrDefault() {
    return dineIn == null ? true : dineIn;
  }

  public boolean takeOutOrDefault() {
    return takeOut != null && takeOut;
  }

  public boolean deliveryOrDefault() {
    return delivery != null && delivery;
  }
}
