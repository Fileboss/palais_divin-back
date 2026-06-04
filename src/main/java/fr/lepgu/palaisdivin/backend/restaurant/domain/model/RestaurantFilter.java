package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.util.List;

public record RestaurantFilter(List<String> tagSlugs, String name) {

  public RestaurantFilter {
    tagSlugs = tagSlugs == null ? List.of() : List.copyOf(tagSlugs);
  }

  public static RestaurantFilter none() {
    return new RestaurantFilter(List.of(), null);
  }

  public boolean hasTags() {
    return !tagSlugs.isEmpty();
  }

  public boolean hasName() {
    return name != null;
  }
}
