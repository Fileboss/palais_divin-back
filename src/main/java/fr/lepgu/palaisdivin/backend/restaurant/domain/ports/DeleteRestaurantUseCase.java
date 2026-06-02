package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;

public interface DeleteRestaurantUseCase {

  void delete(RestaurantId id);
}
