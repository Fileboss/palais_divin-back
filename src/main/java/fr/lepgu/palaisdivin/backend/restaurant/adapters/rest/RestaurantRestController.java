package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/public/restaurants")
class RestaurantRestController {

  private final CreateRestaurantUseCase createRestaurant;
  private final FindRestaurantUseCase findRestaurant;

  RestaurantRestController(
      CreateRestaurantUseCase createRestaurant, FindRestaurantUseCase findRestaurant) {
    this.createRestaurant = createRestaurant;
    this.findRestaurant = findRestaurant;
  }

  @PostMapping
  ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest req) {
    Restaurant created =
        createRestaurant.create(
            req.name(),
            req.address(),
            new Coordinates(req.location().latitude(), req.location().longitude()));
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
}
