package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummary;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.util.Collection;
import java.util.Map;

public interface LoadRestaurantThumbnailsUseCase {

  Map<RestaurantId, PhotoSummary> load(Collection<RestaurantId> restaurantIds);
}
