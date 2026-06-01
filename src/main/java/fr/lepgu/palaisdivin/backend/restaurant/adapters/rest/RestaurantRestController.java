package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Validated
@RequestMapping("/api/v1/user/restaurants")
class RestaurantRestController {

  private final CreateRestaurantUseCase createRestaurant;
  private final FindRestaurantUseCase findRestaurant;
  private final ListRestaurantsUseCase listRestaurants;

  RestaurantRestController(
      CreateRestaurantUseCase createRestaurant,
      FindRestaurantUseCase findRestaurant,
      ListRestaurantsUseCase listRestaurants) {
    this.createRestaurant = createRestaurant;
    this.findRestaurant = findRestaurant;
    this.listRestaurants = listRestaurants;
  }

  @PostMapping
  ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest req) {
    Restaurant created = createRestaurant.create(req.name(), req.address());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id().value())
            .toUri();
    return ResponseEntity.created(location).body(RestaurantResponse.from(created));
  }

  @GetMapping("/{id}")
  RestaurantResponse get(@PathVariable UUID id) {
    RestaurantId restaurantId = new RestaurantId(id);
    return findRestaurant
        .findById(restaurantId)
        .map(RestaurantResponse::from)
        .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
  }

  @GetMapping
  RestaurantsPageResponse list(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "CREATED_AT_DESC") RestaurantSort sort) {
    RestaurantCursor decoded = cursor == null ? null : CursorCodec.decode(cursor);
    CursorPage<Restaurant> page = listRestaurants.list(decoded, size);
    List<RestaurantResponse> data = page.data().stream().map(RestaurantResponse::from).toList();
    String nextCursor =
        page.hasNext() && !data.isEmpty()
            ? CursorCodec.encode(
                new RestaurantCursor(
                    page.data().getLast().createdAt(), page.data().getLast().id().value()))
            : null;
    return new RestaurantsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }
}
