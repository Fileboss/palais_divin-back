package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.AttachResult;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;

public interface AttachTagUseCase {

  AttachResult attach(String subject, RestaurantId restaurantId, TagId tagId);
}
