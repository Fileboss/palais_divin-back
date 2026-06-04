package fr.lepgu.palaisdivin.backend.restaurant.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.events.RestaurantCreated;
import fr.lepgu.palaisdivin.backend.restaurant.domain.events.RestaurantDeleted;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.DeleteRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.GeocoderPort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantService
    implements CreateRestaurantUseCase,
        FindRestaurantUseCase,
        ListRestaurantsUseCase,
        DeleteRestaurantUseCase {

  private final RestaurantRepositoryPort repository;
  private final GeocoderPort geocoder;
  private final OutboxPublisher outboxPublisher;
  private final Clock clock;

  public RestaurantService(
      RestaurantRepositoryPort repository,
      GeocoderPort geocoder,
      OutboxPublisher outboxPublisher,
      Clock clock) {
    this.repository = repository;
    this.geocoder = geocoder;
    this.outboxPublisher = outboxPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Restaurant create(String name, String address) {
    Coordinates location = geocoder.geocode(address);
    Restaurant restaurant =
        new Restaurant(RestaurantId.newId(), name, address, location, Instant.now(clock), null);
    Restaurant saved = repository.save(restaurant);
    outboxPublisher.publish(
        "Restaurant",
        saved.id().value(),
        "RestaurantCreated",
        new RestaurantCreated(
            saved.id().value(),
            saved.name(),
            saved.address(),
            saved.location().latitude(),
            saved.location().longitude(),
            saved.createdAt()));
    return saved;
  }

  @Override
  public Optional<Restaurant> findById(RestaurantId id) {
    return repository.findById(id);
  }

  @Override
  public CursorPage<Restaurant> list(RestaurantCursor cursor, int size, RestaurantFilter filter) {
    return repository.findAll(cursor, size, filter);
  }

  @Override
  @Transactional
  public void delete(RestaurantId id) {
    repository.findById(id).orElseThrow(() -> new RestaurantNotFoundException(id));
    repository.deleteById(id);
    outboxPublisher.publish(
        "Restaurant",
        id.value(),
        "RestaurantDeleted",
        new RestaurantDeleted(id.value(), Instant.now(clock)));
  }
}
