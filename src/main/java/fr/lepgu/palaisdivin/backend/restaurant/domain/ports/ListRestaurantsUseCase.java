package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.Page;

public interface ListRestaurantsUseCase {

  Page<Restaurant> list(RestaurantCursor cursor, int size);
}
