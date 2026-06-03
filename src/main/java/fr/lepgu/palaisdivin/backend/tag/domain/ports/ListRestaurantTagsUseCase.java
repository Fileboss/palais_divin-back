package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ListRestaurantTagsUseCase {

  List<Tag> listFor(RestaurantId restaurantId);

  Map<RestaurantId, List<Tag>> listFor(Collection<RestaurantId> restaurantIds);
}
