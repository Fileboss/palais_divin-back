package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.util.Optional;

public interface RegisterPhotoUseCase {
  Photo register(
      String authorSubject,
      RestaurantId restaurantId,
      String objectKey,
      String contentType,
      Optional<String> idempotencyKey);
}
