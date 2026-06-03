package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import java.util.List;
import java.util.Optional;

public interface RestaurantTagRepositoryPort {

  Optional<RestaurantTag> findByRestaurantAndTag(RestaurantId restaurantId, TagId tagId);

  RestaurantTag save(RestaurantTag attachment);

  boolean delete(RestaurantId restaurantId, TagId tagId);

  List<Tag> findTagsByRestaurant(RestaurantId restaurantId);
}
