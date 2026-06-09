package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import java.util.List;
import java.util.Set;

public record RestaurantFilter(
    List<List<String>> tagSlugGroups,
    String name,
    Coordinates anchor,
    Set<RestaurantId> idsAllowList,
    Boolean dineIn,
    Boolean takeOut,
    Boolean delivery) {

  public RestaurantFilter {
    tagSlugGroups = copyGroups(tagSlugGroups);
    idsAllowList = idsAllowList == null ? Set.of() : Set.copyOf(idsAllowList);
  }

  public RestaurantFilter(
      List<String> tagSlugs, String name, Coordinates anchor, Set<RestaurantId> idsAllowList) {
    this(toSingletonGroups(tagSlugs), name, anchor, idsAllowList, null, null, null);
  }

  public static RestaurantFilter ofTagSlugs(
      List<String> tagSlugs,
      String name,
      Coordinates anchor,
      Set<RestaurantId> idsAllowList,
      Boolean dineIn,
      Boolean takeOut,
      Boolean delivery) {
    return new RestaurantFilter(
        toSingletonGroups(tagSlugs), name, anchor, idsAllowList, dineIn, takeOut, delivery);
  }

  public RestaurantFilter(List<String> tagSlugs, String name, Coordinates anchor) {
    this(toSingletonGroups(tagSlugs), name, anchor, null, null, null, null);
  }

  public RestaurantFilter(List<String> tagSlugs, String name) {
    this(toSingletonGroups(tagSlugs), name, null, null, null, null, null);
  }

  public static RestaurantFilter none() {
    return new RestaurantFilter(List.<List<String>>of(), null, null, null, null, null, null);
  }

  public boolean hasTags() {
    return !tagSlugGroups.isEmpty();
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

  public boolean hasDineIn() {
    return dineIn != null;
  }

  public boolean hasTakeOut() {
    return takeOut != null;
  }

  public boolean hasDelivery() {
    return delivery != null;
  }

  private static List<List<String>> copyGroups(List<List<String>> groups) {
    if (groups == null || groups.isEmpty()) {
      return List.of();
    }
    return groups.stream().map(List::copyOf).toList();
  }

  private static List<List<String>> toSingletonGroups(List<String> tagSlugs) {
    if (tagSlugs == null || tagSlugs.isEmpty()) {
      return List.of();
    }
    return tagSlugs.stream().map(List::of).toList();
  }
}
