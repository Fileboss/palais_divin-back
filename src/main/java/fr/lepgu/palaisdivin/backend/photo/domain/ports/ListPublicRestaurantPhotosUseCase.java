package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummaryPage;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;

public interface ListPublicRestaurantPhotosUseCase {

  PhotoSummaryPage list(RestaurantId restaurantId, PhotoCursor cursor, int size);
}
