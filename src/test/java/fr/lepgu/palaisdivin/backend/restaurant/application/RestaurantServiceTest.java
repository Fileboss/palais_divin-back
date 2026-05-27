package fr.lepgu.palaisdivin.backend.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-27T10:00:00Z");
  private static final Coordinates LOCATION = new Coordinates(48.8566, 2.3522);

  @Mock private RestaurantRepositoryPort repository;

  private RestaurantService service;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    service = new RestaurantService(repository, fixedClock);
  }

  @Test
  void createPersistsRestaurantWithGeneratedIdAndClockTimestamp() {
    when(repository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

    Restaurant created = service.create("Septime", "80 Rue de Charonne", LOCATION);

    ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
    verify(repository).save(captor.capture());
    Restaurant persisted = captor.getValue();

    assertThat(persisted.id()).isNotNull();
    assertThat(persisted.id().value()).isNotNull();
    assertThat(persisted.name()).isEqualTo("Septime");
    assertThat(persisted.address()).isEqualTo("80 Rue de Charonne");
    assertThat(persisted.location()).isEqualTo(LOCATION);
    assertThat(persisted.createdAt()).isEqualTo(FIXED_NOW);
    assertThat(created).isEqualTo(persisted);
  }

  @Test
  void createReturnsWhateverRepositorySaveReturns() {
    Restaurant fromStore =
        new Restaurant(RestaurantId.newId(), "Septime", null, LOCATION, FIXED_NOW);
    when(repository.save(any(Restaurant.class))).thenReturn(fromStore);

    Restaurant returned = service.create("Septime", null, LOCATION);

    assertThat(returned).isSameAs(fromStore);
  }

  @Test
  void findByIdDelegatesToRepositoryAndReturnsResult() {
    RestaurantId id = RestaurantId.newId();
    Restaurant stored = new Restaurant(id, "Septime", null, LOCATION, FIXED_NOW);
    when(repository.findById(id)).thenReturn(Optional.of(stored));

    Optional<Restaurant> found = service.findById(id);

    assertThat(found).containsSame(stored);
  }

  @Test
  void findByIdReturnsEmptyWhenRepositoryHasNoMatch() {
    RestaurantId id = RestaurantId.newId();
    when(repository.findById(id)).thenReturn(Optional.empty());

    Optional<Restaurant> found = service.findById(id);

    assertThat(found).isEmpty();
  }
}
