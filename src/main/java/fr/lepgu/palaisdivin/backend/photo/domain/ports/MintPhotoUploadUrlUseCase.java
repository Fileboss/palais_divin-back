package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;

public interface MintPhotoUploadUrlUseCase {

  PhotoUploadUrl mint(RestaurantId restaurantId);
}
