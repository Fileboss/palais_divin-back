package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRestaurantRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
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
