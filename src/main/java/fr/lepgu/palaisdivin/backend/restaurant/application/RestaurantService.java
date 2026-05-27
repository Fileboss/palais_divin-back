package fr.lepgu.palaisdivin.backend.restaurant.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class RestaurantService implements CreateRestaurantUseCase, FindRestaurantUseCase {

  private final RestaurantRepositoryPort repository;
  private final Clock clock;

  public RestaurantService(RestaurantRepositoryPort repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public Restaurant create(String name, String address, Coordinates location) {
    Restaurant restaurant =
        new Restaurant(RestaurantId.newId(), name, address, location, Instant.now(clock));
    return repository.save(restaurant);
  }

  @Override
  public Optional<Restaurant> findById(RestaurantId id) {
    return repository.findById(id);
  }
}
