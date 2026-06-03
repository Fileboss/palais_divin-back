package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;

public interface DetachTagUseCase {

  void detach(RestaurantId restaurantId, TagId tagId);
}
