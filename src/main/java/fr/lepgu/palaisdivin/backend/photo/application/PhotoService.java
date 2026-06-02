package fr.lepgu.palaisdivin.backend.photo.application;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.InvalidObjectKeyException;
import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoUploadUrlUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoRepositoryPort;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.RegisterPhotoUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.IdempotencyKeyPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PhotoService implements MintPhotoUploadUrlUseCase, RegisterPhotoUseCase {

  private static final String AGGREGATE_TYPE = "Photo";
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
  private static final Pattern UUID_SUFFIX =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  private final RestaurantRepositoryPort restaurants;
  private final PhotoStoragePort storage;
  private final PhotoRepositoryPort photos;
  private final UserRepositoryPort users;
  private final IdempotencyKeyPort idempotency;
  private final MinioProperties properties;
  private final Clock clock;

  public PhotoService(
      RestaurantRepositoryPort restaurants,
      PhotoStoragePort storage,
      PhotoRepositoryPort photos,
      UserRepositoryPort users,
      IdempotencyKeyPort idempotency,
      MinioProperties properties,
      Clock clock) {
    this.restaurants = restaurants;
    this.storage = storage;
    this.photos = photos;
    this.users = users;
    this.idempotency = idempotency;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public PhotoUploadUrl mint(RestaurantId restaurantId) {
    if (restaurants.findById(restaurantId).isEmpty()) {
      throw new RestaurantNotFoundException(restaurantId);
    }
    String objectKey = "restaurants/" + restaurantId.value() + "/" + UUID.randomUUID();
    Duration ttl = properties.uploadUrlTtl();
    URI uploadUrl = storage.presignPut(objectKey, ttl);
    return new PhotoUploadUrl(objectKey, uploadUrl, clock.instant().plus(ttl));
  }

  @Override
  public Photo register(
      String authorSubject,
      RestaurantId restaurantId,
      String objectKey,
      String contentType,
      Optional<String> idempotencyKey) {
    String expectedPrefix = "restaurants/" + restaurantId.value() + "/";
    if (!objectKey.startsWith(expectedPrefix)
        || !UUID_SUFFIX.matcher(objectKey.substring(expectedPrefix.length())).matches()) {
      throw new InvalidObjectKeyException(
          "Object key must match " + expectedPrefix + "{uuid}, got " + objectKey);
    }

    UserId authorId = users.requireBySubject(authorSubject);

    if (idempotencyKey.isPresent()) {
      Optional<UUID> existingId =
          idempotency.findRecent(idempotencyKey.get(), authorId, AGGREGATE_TYPE, IDEMPOTENCY_TTL);
      if (existingId.isPresent()) {
        return photos
            .findById(new PhotoId(existingId.get()))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Idempotency row points at missing photo " + existingId.get()));
      }
    }

    if (restaurants.findById(restaurantId).isEmpty()) {
      throw new RestaurantNotFoundException(restaurantId);
    }

    Photo photo =
        new Photo(PhotoId.newId(), restaurantId, authorId, objectKey, contentType, clock.instant());
    Photo saved = photos.save(photo);

    idempotencyKey.ifPresent(
        key -> idempotency.record(key, authorId, AGGREGATE_TYPE, saved.id().value()));

    return saved;
  }
}
