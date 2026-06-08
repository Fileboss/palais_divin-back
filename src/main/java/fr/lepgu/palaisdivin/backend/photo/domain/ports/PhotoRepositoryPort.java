package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface PhotoRepositoryPort {

  Photo save(Photo photo);

  Optional<Photo> findById(PhotoId id);

  Map<RestaurantId, Photo> findOldestByRestaurantIds(Collection<RestaurantId> restaurantIds);

  CursorPage<Photo> findByRestaurantId(RestaurantId restaurantId, PhotoCursor cursor, int size);
}
