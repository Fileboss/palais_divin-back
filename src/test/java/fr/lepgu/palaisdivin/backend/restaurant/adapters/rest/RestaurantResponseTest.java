package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestaurantResponseTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-03T12:00:00Z");

  private static Restaurant restaurant() {
    return new Restaurant(
        RestaurantId.newId(),
        "Septime",
        "80 Rue de Charonne",
        new Coordinates(48.85, 2.38),
        CREATED_AT,
        null);
  }

  @Test
  void single_arg_factory_emits_empty_tags() {
    RestaurantResponse resp = RestaurantResponse.from(restaurant());
    assertThat(resp.tags()).isEmpty();
  }

  @Test
  void two_arg_factory_maps_tags_preserving_order_and_strips_id() {
    Tag a =
        new Tag(
            TagId.newId(),
            TagCategory.SPECIALTY,
            "natural-wine",
            "Natural wine",
            Map.of(),
            CREATED_AT);
    Tag b = new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", Map.of(), CREATED_AT);

    RestaurantResponse resp = RestaurantResponse.from(restaurant(), List.of(a, b));

    assertThat(resp.tags())
        .extracting(
            RestaurantResponse.TagSummary::category,
            RestaurantResponse.TagSummary::slug,
            RestaurantResponse.TagSummary::label)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                TagCategory.SPECIALTY, "natural-wine", "Natural wine"),
            org.assertj.core.groups.Tuple.tuple(TagCategory.REGIME, "vegan", "Vegan"));
  }
}
