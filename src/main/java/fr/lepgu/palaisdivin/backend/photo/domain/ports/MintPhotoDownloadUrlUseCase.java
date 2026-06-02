package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoDownloadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;

public interface MintPhotoDownloadUrlUseCase {

  PhotoDownloadUrl mint(RestaurantId restaurantId, PhotoId photoId);
}
