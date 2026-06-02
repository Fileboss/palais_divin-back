package fr.lepgu.palaisdivin.backend.photo.application;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoUploadUrlUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PhotoService implements MintPhotoUploadUrlUseCase {

  private final RestaurantRepositoryPort restaurants;
  private final PhotoStoragePort storage;
  private final MinioProperties properties;
  private final Clock clock;

  public PhotoService(
      RestaurantRepositoryPort restaurants,
      PhotoStoragePort storage,
      MinioProperties properties,
      Clock clock) {
    this.restaurants = restaurants;
    this.storage = storage;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public PhotoUploadUrl mint(RestaurantId restaurantId) {
    if (restaurants.findById(restaurantId).isEmpty()) {
      throw new RestaurantNotFoundException(restaurantId);
    }
    String objectKey = "restaurants/" + restaurantId.value() + "/" + UUID.randomUUID();
    Duration ttl = properties.uploadUrlTtl();
    URI uploadUrl = storage.presignPut(objectKey, ttl);
    return new PhotoUploadUrl(objectKey, uploadUrl, clock.instant().plus(ttl));
  }
}
