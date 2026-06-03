package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListRestaurantTagsUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/restaurants")
class PublicRestaurantRestController {

  private final FindRestaurantUseCase findRestaurant;
  private final ListRestaurantsUseCase listRestaurants;
  private final ListRestaurantTagsUseCase listRestaurantTags;

  PublicRestaurantRestController(
      FindRestaurantUseCase findRestaurant,
      ListRestaurantsUseCase listRestaurants,
      ListRestaurantTagsUseCase listRestaurantTags) {
    this.findRestaurant = findRestaurant;
    this.listRestaurants = listRestaurants;
    this.listRestaurantTags = listRestaurantTags;
  }

  @GetMapping("/{id}")
  RestaurantResponse get(@PathVariable UUID id) {
    RestaurantId restaurantId = new RestaurantId(id);
    Restaurant restaurant =
        findRestaurant
            .findById(restaurantId)
            .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    return RestaurantResponse.from(restaurant, listRestaurantTags.listFor(restaurantId));
  }

  @GetMapping
  RestaurantsPageResponse list(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "CREATED_AT_DESC") RestaurantSort sort,
      @RequestParam(name = "tag", required = false) @Size(max = 10)
          List<@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") @Size(max = 64) String> tag) {
    List<String> tagSlugs = tag == null ? List.of() : tag;
    RestaurantCursor decoded = cursor == null ? null : CursorCodec.decode(cursor);
    CursorPage<Restaurant> page = listRestaurants.list(decoded, size, tagSlugs);
    List<RestaurantId> ids = page.data().stream().map(Restaurant::id).toList();
    Map<RestaurantId, List<Tag>> tagsByRestaurant = listRestaurantTags.listFor(ids);
    List<RestaurantResponse> data =
        page.data().stream()
            .map(r -> RestaurantResponse.from(r, tagsByRestaurant.getOrDefault(r.id(), List.of())))
            .toList();
    String nextCursor =
        page.hasNext() && !data.isEmpty()
            ? CursorCodec.encode(
                new RestaurantCursor(
                    page.data().getLast().createdAt(), page.data().getLast().id().value()))
            : null;
    return new RestaurantsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }
}
