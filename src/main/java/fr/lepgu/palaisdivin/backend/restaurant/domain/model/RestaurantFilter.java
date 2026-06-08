package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.util.List;
import java.util.Set;

public record RestaurantFilter(
    List<String> tagSlugs, String name, Coordinates anchor, Set<RestaurantId> idsAllowList) {

  public RestaurantFilter {
    tagSlugs = tagSlugs == null ? List.of() : List.copyOf(tagSlugs);
    idsAllowList = idsAllowList == null ? Set.of() : Set.copyOf(idsAllowList);
  }

  public RestaurantFilter(List<String> tagSlugs, String name, Coordinates anchor) {
    this(tagSlugs, name, anchor, null);
  }

  public RestaurantFilter(List<String> tagSlugs, String name) {
    this(tagSlugs, name, null, null);
  }

  public static RestaurantFilter none() {
    return new RestaurantFilter(List.of(), null, null, null);
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

  public boolean hasIdsAllowList() {
    return !idsAllowList.isEmpty();
  }
}
