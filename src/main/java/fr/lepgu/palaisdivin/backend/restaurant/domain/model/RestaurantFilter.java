package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.util.List;

public record RestaurantFilter(List<String> tagSlugs, String name, Coordinates anchor) {

  public RestaurantFilter {
    tagSlugs = tagSlugs == null ? List.of() : List.copyOf(tagSlugs);
  }

  public RestaurantFilter(List<String> tagSlugs, String name) {
    this(tagSlugs, name, null);
  }

  public static RestaurantFilter none() {
    return new RestaurantFilter(List.of(), null, null);
  }

  public boolean hasTags() {
    return !tagSlugs.isEmpty();
  }

  public boolean hasName() {
    return name != null;
  }

  public boolean hasAnchor() {
    return anchor != null;
  }
}
