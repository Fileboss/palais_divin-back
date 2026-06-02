package fr.lepgu.palaisdivin.backend.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.PhotoStorageException;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-02T12:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final URI SIGNED_URL =
      URI.create("http://minio.test/palaisdivin-photos/key?X-Amz-Signature=abc");

  @Mock RestaurantRepositoryPort restaurants;
  @Mock PhotoStoragePort storage;

  PhotoService service;
  MinioProperties properties;
  RestaurantId restaurantId;
  Restaurant restaurant;

  @BeforeEach
  void setUp() {
    properties =
        new MinioProperties(
            "http://minio.test", "k", "s", Duration.ofSeconds(2), "palaisdivin-photos", TTL);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new PhotoService(restaurants, storage, properties, clock);

    restaurantId = RestaurantId.newId();
    restaurant =
        new Restaurant(
            restaurantId,
            "Septime",
            "addr",
            new Coordinates(48.8, 2.3),
            NOW.minusSeconds(60),
            null);
  }

  @Test
  void mintReturnsKeyUrlAndExpiry() {
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(storage.presignPut(any(), eq(TTL))).thenReturn(SIGNED_URL);

    PhotoUploadUrl result = service.mint(restaurantId);

    assertThat(result.objectKey()).startsWith("restaurants/" + restaurantId.value() + "/");
    assertThat(result.uploadUrl()).isEqualTo(SIGNED_URL);
    assertThat(result.expiresAt()).isEqualTo(NOW.plus(TTL));
    verify(storage).presignPut(result.objectKey(), TTL);
  }

  @Test
  void mintThrowsWhenRestaurantMissing() {
    when(restaurants.findById(restaurantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.mint(restaurantId))
        .isInstanceOf(RestaurantNotFoundException.class);

    verify(storage, never()).presignPut(any(), any());
  }

  @Test
  void mintPropagatesStorageFailure() {
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(storage.presignPut(any(), eq(TTL)))
        .thenThrow(new PhotoStorageException("boom", new RuntimeException()));

    assertThatThrownBy(() -> service.mint(restaurantId)).isInstanceOf(PhotoStorageException.class);
  }
}
