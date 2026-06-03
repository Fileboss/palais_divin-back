package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import java.util.List;

public interface ListRestaurantTagsUseCase {

  List<Tag> listFor(RestaurantId restaurantId);
}
