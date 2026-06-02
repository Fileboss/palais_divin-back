package fr.lepgu.palaisdivin.backend.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.InvalidObjectKeyException;
import fr.lepgu.palaisdivin.backend.photo.domain.PhotoStorageException;
import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoRepositoryPort;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.IdempotencyKeyPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-02T12:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final String SUBJECT = "kc-subject-xyz";
  private static final URI SIGNED_URL =
      URI.create("http://minio.test/palaisdivin-photos/key?X-Amz-Signature=abc");

  @Mock RestaurantRepositoryPort restaurants;
  @Mock PhotoStoragePort storage;
  @Mock PhotoRepositoryPort photos;
  @Mock UserRepositoryPort users;
  @Mock IdempotencyKeyPort idempotency;

  PhotoService service;
  MinioProperties properties;
  RestaurantId restaurantId;
  UserId authorId;
  Restaurant restaurant;
  String validObjectKey;

  @BeforeEach
  void setUp() {
    properties =
        new MinioProperties(
            "http://minio.test", "k", "s", Duration.ofSeconds(2), "palaisdivin-photos", TTL);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new PhotoService(restaurants, storage, photos, users, idempotency, properties, clock);

    restaurantId = RestaurantId.newId();
    authorId = UserId.newId();
    restaurant =
        new Restaurant(
            restaurantId,
            "Septime",
            "addr",
            new Coordinates(48.8, 2.3),
            NOW.minusSeconds(60),
            null);
    validObjectKey = "restaurants/" + restaurantId.value() + "/" + UUID.randomUUID();
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

  @Test
  void registerPersistsPhotoAndReturnsIt() {
    when(users.requireBySubject(SUBJECT)).thenReturn(authorId);
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(photos.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

    Photo result =
        service.register(SUBJECT, restaurantId, validObjectKey, "image/jpeg", Optional.empty());

    assertThat(result.restaurantId()).isEqualTo(restaurantId);
    assertThat(result.authorId()).isEqualTo(authorId);
    assertThat(result.objectKey()).isEqualTo(validObjectKey);
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.createdAt()).isEqualTo(NOW);
    verify(idempotency, never()).record(any(), any(), any(), any());
  }

  @Test
  void registerThrowsWhenObjectKeyHasWrongPrefix() {
    UUID otherRestaurant = UUID.randomUUID();
    String foreignKey = "restaurants/" + otherRestaurant + "/" + UUID.randomUUID();

    assertThatThrownBy(
            () ->
                service.register(SUBJECT, restaurantId, foreignKey, "image/jpeg", Optional.empty()))
        .isInstanceOf(InvalidObjectKeyException.class);

    verify(photos, never()).save(any());
    verifyNoInteractions(users, idempotency);
  }

  @Test
  void registerThrowsWhenObjectKeySuffixNotUuid() {
    String badKey = "restaurants/" + restaurantId.value() + "/notauuid";

    assertThatThrownBy(
            () -> service.register(SUBJECT, restaurantId, badKey, "image/jpeg", Optional.empty()))
        .isInstanceOf(InvalidObjectKeyException.class);

    verify(photos, never()).save(any());
  }

  @Test
  void registerThrowsWhenRestaurantMissing() {
    when(users.requireBySubject(SUBJECT)).thenReturn(authorId);
    when(restaurants.findById(restaurantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.register(
                    SUBJECT, restaurantId, validObjectKey, "image/jpeg", Optional.empty()))
        .isInstanceOf(RestaurantNotFoundException.class);

    verify(photos, never()).save(any());
  }

  @Test
  void registerWithIdempotencyKeyHitReplaysExisting() {
    PhotoId existingId = PhotoId.newId();
    Photo existing =
        new Photo(
            existingId,
            restaurantId,
            authorId,
            validObjectKey,
            "image/png",
            NOW.minusSeconds(3600));
    when(users.requireBySubject(SUBJECT)).thenReturn(authorId);
    when(idempotency.findRecent("KEY-1", authorId, "Photo", Duration.ofHours(24)))
        .thenReturn(Optional.of(existingId.value()));
    when(photos.findById(existingId)).thenReturn(Optional.of(existing));

    Photo result =
        service.register(SUBJECT, restaurantId, validObjectKey, "image/jpeg", Optional.of("KEY-1"));

    assertThat(result).isEqualTo(existing);
    verify(photos, never()).save(any());
    verify(restaurants, never()).findById(any());
    verify(idempotency, never()).record(any(), any(), any(), any());
  }

  @Test
  void registerWithIdempotencyKeyMissPersistsAndRecords() {
    when(users.requireBySubject(SUBJECT)).thenReturn(authorId);
    when(idempotency.findRecent("KEY-2", authorId, "Photo", Duration.ofHours(24)))
        .thenReturn(Optional.empty());
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(photos.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

    Photo result =
        service.register(SUBJECT, restaurantId, validObjectKey, "image/jpeg", Optional.of("KEY-2"));

    verify(idempotency).record("KEY-2", authorId, "Photo", result.id().value());
  }
}
