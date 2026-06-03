package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record TagCatalogResponse(List<CategoryGroup> groups) {

  public record CategoryGroup(TagCategory category, List<TagResponse> tags) {}

  public static TagCatalogResponse from(List<Tag> tags) {
    Map<TagCategory, List<TagResponse>> bucket =
        tags.stream()
            .collect(
                Collectors.groupingBy(
                    Tag::category,
                    () -> new EnumMap<>(TagCategory.class),
                    Collectors.mapping(TagResponse::from, Collectors.toList())));
    List<CategoryGroup> groups =
        Arrays.stream(TagCategory.values())
            .map(c -> new CategoryGroup(c, bucket.getOrDefault(c, List.of())))
            .toList();
    return new TagCatalogResponse(groups);
  }
}
