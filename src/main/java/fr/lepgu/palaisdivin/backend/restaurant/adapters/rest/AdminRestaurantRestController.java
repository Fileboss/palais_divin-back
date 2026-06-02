package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.DeleteRestaurantUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/restaurants")
class AdminRestaurantRestController {

  private final DeleteRestaurantUseCase deleteRestaurant;

  AdminRestaurantRestController(DeleteRestaurantUseCase deleteRestaurant) {
    this.deleteRestaurant = deleteRestaurant;
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id) {
    deleteRestaurant.delete(new RestaurantId(id));
    return ResponseEntity.noContent().build();
  }
}
