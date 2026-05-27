package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.Page;
import java.util.Optional;

public interface RestaurantRepositoryPort {

  Restaurant save(Restaurant restaurant);

  Optional<Restaurant> findById(RestaurantId id);

  Page<Restaurant> findAll(RestaurantCursor cursor, int size);
}
