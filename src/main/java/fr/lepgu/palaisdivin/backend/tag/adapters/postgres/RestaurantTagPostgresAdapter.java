package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.RestaurantTagRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

  @Override
  public Map<RestaurantId, List<Tag>> findTagsByRestaurants(
      Collection<RestaurantId> restaurantIds) {
    if (restaurantIds.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = restaurantIds.stream().map(RestaurantId::value).toList();
    Map<RestaurantId, List<Tag>> grouped = new LinkedHashMap<>();
    for (Object[] row : jpa.findTagsForRestaurants(ids)) {
      RestaurantId restaurantId = new RestaurantId((UUID) row[0]);
      Tag tag = TagPostgresAdapter.toDomain((TagEntity) row[1]);
      grouped.computeIfAbsent(restaurantId, k -> new ArrayList<>()).add(tag);
    }
    return grouped;
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
