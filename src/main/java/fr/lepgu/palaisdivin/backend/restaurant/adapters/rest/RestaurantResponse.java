package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RestaurantResponse(
    UUID id,
    String name,
    String address,
    CoordinatesDto location,
    Instant createdAt,
    Double avgRating,
    Double distanceMetres,
    List<TagSummary> tags) {

  public static RestaurantResponse from(Restaurant r) {
    return from(r, List.of());
  }

  public static RestaurantResponse from(Restaurant r, List<Tag> tags) {
    return new RestaurantResponse(
        r.id().value(),
        r.name(),
        r.address(),
        new CoordinatesDto(r.location().latitude(), r.location().longitude()),
        r.createdAt(),
        r.avgRating(),
        r.distanceMetres(),
        tags.stream().map(TagSummary::from).toList());
  }

  public record TagSummary(TagCategory category, String slug, String label) {
    public static TagSummary from(Tag t) {
      return new TagSummary(t.category(), t.slug(), t.label());
    }
  }
}
