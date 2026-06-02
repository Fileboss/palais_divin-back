package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/user/restaurants")
class RestaurantRestController {

  private final CreateRestaurantUseCase createRestaurant;

  RestaurantRestController(CreateRestaurantUseCase createRestaurant) {
    this.createRestaurant = createRestaurant;
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
}
