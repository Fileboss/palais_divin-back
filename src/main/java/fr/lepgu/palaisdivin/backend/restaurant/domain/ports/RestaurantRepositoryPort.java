package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface RestaurantRepositoryPort {

  Restaurant save(Restaurant restaurant);

  Optional<Restaurant> findById(RestaurantId id);

  Map<RestaurantId, Restaurant> findByIds(Collection<RestaurantId> ids);

  CursorPage<Restaurant> findAll(
      RestaurantCursor cursor, int size, RestaurantFilter filter, RestaurantSort sort);

  void deleteById(RestaurantId id);
}
