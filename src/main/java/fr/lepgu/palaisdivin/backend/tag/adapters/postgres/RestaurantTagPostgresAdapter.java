package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.RestaurantTagRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantTagPostgresAdapter implements RestaurantTagRepositoryPort {

  private final RestaurantTagJpaRepository jpa;

  RestaurantTagPostgresAdapter(RestaurantTagJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<RestaurantTag> findByRestaurantAndTag(RestaurantId restaurantId, TagId tagId) {
    return jpa.findByRestaurantIdAndTagId(restaurantId.value(), tagId.value())
        .map(RestaurantTagPostgresAdapter::toDomain);
  }

  @Override
  public RestaurantTag save(RestaurantTag attachment) {
    return toDomain(jpa.save(toEntity(attachment)));
  }

  @Override
  public boolean delete(RestaurantId restaurantId, TagId tagId) {
    return jpa.deleteByRestaurantIdAndTagId(restaurantId.value(), tagId.value()) > 0;
  }

  @Override
  public List<Tag> findTagsByRestaurant(RestaurantId restaurantId) {
    return jpa.findTagsByRestaurantId(restaurantId.value()).stream()
        .map(TagPostgresAdapter::toDomain)
        .toList();
  }

  private static RestaurantTagEntity toEntity(RestaurantTag a) {
    return new RestaurantTagEntity(
        a.restaurantId().value(), a.tagId().value(), a.attachedBy().value(), a.attachedAt());
  }

  private static RestaurantTag toDomain(RestaurantTagEntity e) {
    return new RestaurantTag(
        new RestaurantId(e.getRestaurantId()),
        new TagId(e.getTagId()),
        new UserId(e.getAttachedBy()),
        e.getAttachedAt());
  }
}
