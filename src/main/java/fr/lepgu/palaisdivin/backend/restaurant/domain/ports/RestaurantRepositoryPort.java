package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.util.Optional;

public interface RestaurantRepositoryPort {

  Restaurant save(Restaurant restaurant);

  Optional<Restaurant> findById(RestaurantId id);
}
