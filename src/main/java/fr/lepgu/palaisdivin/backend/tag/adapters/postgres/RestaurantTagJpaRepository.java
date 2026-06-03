package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RestaurantTagJpaRepository extends JpaRepository<RestaurantTagEntity, RestaurantTagPk> {

  Optional<RestaurantTagEntity> findByRestaurantIdAndTagId(UUID restaurantId, UUID tagId);

  @Modifying
  long deleteByRestaurantIdAndTagId(UUID restaurantId, UUID tagId);

  @Query(
      """
      select t from TagEntity t
      join RestaurantTagEntity rt on rt.tagId = t.id
      where rt.restaurantId = :restaurantId
      order by t.category asc, t.slug asc
      """)
  List<TagEntity> findTagsByRestaurantId(@Param("restaurantId") UUID restaurantId);

  @Query(
      """
      select rt.restaurantId, t from TagEntity t
      join RestaurantTagEntity rt on rt.tagId = t.id
      where rt.restaurantId in :restaurantIds
      order by rt.restaurantId asc, t.category asc, t.slug asc
      """)
  List<Object[]> findTagsForRestaurants(@Param("restaurantIds") Collection<UUID> restaurantIds);
}
