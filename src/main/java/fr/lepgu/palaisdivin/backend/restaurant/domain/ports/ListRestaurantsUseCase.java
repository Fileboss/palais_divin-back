package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.util.List;

public interface ListRestaurantsUseCase {

  CursorPage<Restaurant> list(RestaurantCursor cursor, int size, List<String> tagSlugs);
}
