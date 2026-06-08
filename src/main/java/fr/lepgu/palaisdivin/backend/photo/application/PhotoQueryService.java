package fr.lepgu.palaisdivin.backend.photo.application;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummary;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummaryPage;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.ListPublicRestaurantPhotosUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.LoadRestaurantThumbnailsUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoRepositoryPort;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PhotoQueryService
    implements ListPublicRestaurantPhotosUseCase, LoadRestaurantThumbnailsUseCase {

  private final PhotoRepositoryPort photos;
  private final PhotoStoragePort storage;
  private final MinioProperties properties;
  private final Clock clock;

  public PhotoQueryService(
      PhotoRepositoryPort photos,
      PhotoStoragePort storage,
      MinioProperties properties,
      Clock clock) {
    this.photos = photos;
    this.storage = storage;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public PhotoSummaryPage list(RestaurantId restaurantId, PhotoCursor cursor, int size) {
    CursorPage<Photo> page = photos.findByRestaurantId(restaurantId, cursor, size);
    Duration ttl = properties.downloadUrlTtl();
    Instant expiresAt = clock.instant().plus(ttl);
    List<Photo> photoList = page.data();
    List<PhotoSummary> summaries =
        photoList.stream().map(p -> toSummary(p, ttl, expiresAt)).toList();
    PhotoCursor nextCursor =
        page.hasNext() && !photoList.isEmpty()
            ? new PhotoCursor(photoList.getLast().createdAt(), photoList.getLast().id().value())
            : null;
    return new PhotoSummaryPage(summaries, page.hasNext(), nextCursor);
  }

  @Override
  public Map<RestaurantId, PhotoSummary> load(Collection<RestaurantId> restaurantIds) {
    Map<RestaurantId, Photo> oldest = photos.findOldestByRestaurantIds(restaurantIds);
    if (oldest.isEmpty()) {
      return Map.of();
    }
    Duration ttl = properties.downloadUrlTtl();
    Instant expiresAt = clock.instant().plus(ttl);
    Map<RestaurantId, PhotoSummary> result = new LinkedHashMap<>(oldest.size());
    for (Map.Entry<RestaurantId, Photo> entry : oldest.entrySet()) {
      result.put(entry.getKey(), toSummary(entry.getValue(), ttl, expiresAt));
    }
    return Map.copyOf(result);
  }

  private PhotoSummary toSummary(Photo photo, Duration ttl, Instant expiresAt) {
    URI url = storage.presignGet(photo.objectKey(), ttl);
    return new PhotoSummary(photo.id(), url, expiresAt);
  }
}
