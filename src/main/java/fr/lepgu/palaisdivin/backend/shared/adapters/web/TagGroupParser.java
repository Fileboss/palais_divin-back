package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TagGroupParser {

  private static final int MAX_SLUGS_TOTAL = 10;
  private static final int MAX_SLUGS_PER_GROUP = 10;

  private TagGroupParser() {}

  public static List<List<String>> parse(List<String> tag) {
    if (tag == null || tag.isEmpty()) {
      return List.of();
    }
    List<List<String>> groups = new ArrayList<>(tag.size());
    int total = 0;
    for (String element : tag) {
      List<String> group = Arrays.stream(element.split(",")).map(String::trim).toList();
      if (group.isEmpty() || group.size() > MAX_SLUGS_PER_GROUP) {
        throw new IllegalArgumentException("tag group must contain 1-10 slugs");
      }
      total += group.size();
      groups.add(group);
    }
    if (total > MAX_SLUGS_TOTAL) {
      throw new IllegalArgumentException("total tag slugs across groups must not exceed 10");
    }
    return groups;
  }
}
