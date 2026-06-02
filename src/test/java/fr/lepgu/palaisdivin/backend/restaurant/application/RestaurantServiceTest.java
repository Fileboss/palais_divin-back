package fr.lepgu.palaisdivin.backend.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.events.RestaurantCreated;
import fr.lepgu.palaisdivin.backend.restaurant.domain.events.RestaurantDeleted;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.GeocoderPort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
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
  @Mock private GeocoderPort geocoder;
  @Mock private OutboxPublisher outboxPublisher;

  private RestaurantService service;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    service = new RestaurantService(repository, geocoder, outboxPublisher, fixedClock);
  }

  @Test
  void createGeocodesAddressThenPersistsWithGeneratedIdAndClockTimestamp() {
    when(geocoder.geocode("80 Rue de Charonne")).thenReturn(LOCATION);
    when(repository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

    Restaurant created = service.create("Septime", "80 Rue de Charonne");

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
  void createPublishesRestaurantCreatedEventWithFlattenedCoordinatesToOutbox() {
    when(geocoder.geocode("80 Rue de Charonne")).thenReturn(LOCATION);
    when(repository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

    Restaurant created = service.create("Septime", "80 Rue de Charonne");

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxPublisher)
        .publish(
            eq("Restaurant"),
            eq(created.id().value()),
            eq("RestaurantCreated"),
            payloadCaptor.capture());

    assertThat(payloadCaptor.getValue()).isInstanceOf(RestaurantCreated.class);
    RestaurantCreated event = (RestaurantCreated) payloadCaptor.getValue();
    assertThat(event.id()).isEqualTo(created.id().value());
    assertThat(event.name()).isEqualTo("Septime");
    assertThat(event.address()).isEqualTo("80 Rue de Charonne");
    assertThat(event.latitude()).isEqualTo(LOCATION.latitude());
    assertThat(event.longitude()).isEqualTo(LOCATION.longitude());
    assertThat(event.createdAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void createReturnsWhateverRepositorySaveReturns() {
    when(geocoder.geocode("80 Rue de Charonne")).thenReturn(LOCATION);
    Restaurant fromStore =
        new Restaurant(
            RestaurantId.newId(), "Septime", "80 Rue de Charonne", LOCATION, FIXED_NOW, null);
    when(repository.save(any(Restaurant.class))).thenReturn(fromStore);

    Restaurant returned = service.create("Septime", "80 Rue de Charonne");

    assertThat(returned).isSameAs(fromStore);
  }

  @Test
  void createPropagatesGeocoderFailureAndDoesNotPersistOrPublish() {
    when(geocoder.geocode("nope")).thenThrow(new UnresolvableAddressException("nope"));

    assertThatThrownBy(() -> service.create("Septime", "nope"))
        .isInstanceOf(UnresolvableAddressException.class);

    verifyNoInteractions(repository);
    verifyNoInteractions(outboxPublisher);
  }

  @Test
  void createPropagatesPublisherFailureAfterAggregateWasSaved() {
    when(geocoder.geocode("80 Rue de Charonne")).thenReturn(LOCATION);
    when(repository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));
    doThrow(new RuntimeException("outbox down"))
        .when(outboxPublisher)
        .publish(any(), any(UUID.class), any(), any());

    assertThatThrownBy(() -> service.create("Septime", "80 Rue de Charonne"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("outbox down");

    verify(repository).save(any(Restaurant.class));
  }

  @Test
  void findByIdDelegatesToRepositoryAndReturnsResult() {
    RestaurantId id = RestaurantId.newId();
    Restaurant stored = new Restaurant(id, "Septime", null, LOCATION, FIXED_NOW, null);
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

  @Test
  void deleteRemovesAggregateAndPublishesRestaurantDeletedEvent() {
    RestaurantId id = RestaurantId.newId();
    Restaurant stored = new Restaurant(id, "Septime", null, LOCATION, FIXED_NOW, null);
    when(repository.findById(id)).thenReturn(Optional.of(stored));

    service.delete(id);

    verify(repository).deleteById(id);
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxPublisher)
        .publish(
            eq("Restaurant"), eq(id.value()), eq("RestaurantDeleted"), payloadCaptor.capture());
    assertThat(payloadCaptor.getValue()).isInstanceOf(RestaurantDeleted.class);
    RestaurantDeleted event = (RestaurantDeleted) payloadCaptor.getValue();
    assertThat(event.id()).isEqualTo(id.value());
    assertThat(event.deletedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void deleteUnknownIdThrowsRestaurantNotFoundAndDoesNotPublish() {
    RestaurantId id = RestaurantId.newId();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(RestaurantNotFoundException.class);

    verify(repository, never()).deleteById(any());
    verifyNoInteractions(outboxPublisher);
  }

  @Test
  void deletePropagatesRepositoryFailureAndDoesNotPublish() {
    RestaurantId id = RestaurantId.newId();
    Restaurant stored = new Restaurant(id, "Septime", null, LOCATION, FIXED_NOW, null);
    when(repository.findById(id)).thenReturn(Optional.of(stored));
    doThrow(new RuntimeException("db down")).when(repository).deleteById(id);

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db down");

    verifyNoInteractions(outboxPublisher);
  }
}
