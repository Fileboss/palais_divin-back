package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, UUID> {

  @Query(
      """
      select r from RestaurantEntity r
      order by r.createdAt desc, r.id desc
      """)
  Slice<RestaurantEntity> findFirstPage(Pageable pageable);

  @Query(
      """
      select r from RestaurantEntity r
      where r.createdAt < :lastCreatedAt
         or (r.createdAt = :lastCreatedAt and r.id < :lastId)
      order by r.createdAt desc, r.id desc
      """)
  Slice<RestaurantEntity> findAfter(
      @Param("lastCreatedAt") Instant lastCreatedAt,
      @Param("lastId") UUID lastId,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          """
          select r.* from restaurant r
          where r.id in (
              select rt.restaurant_id from restaurant_tag rt
              join tag t on rt.tag_id = t.id
              where t.slug in (:slugs)
              group by rt.restaurant_id
              having count(distinct rt.tag_id) = :slugCount
          )
          order by r.created_at desc, r.id desc
          """)
  Slice<RestaurantEntity> findFirstPageFilteredByTags(
      @Param("slugs") Collection<String> slugs,
      @Param("slugCount") int slugCount,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          """
          select r.* from restaurant r
          where (r.created_at < :lastCreatedAt
              or (r.created_at = :lastCreatedAt and r.id < :lastId))
            and r.id in (
              select rt.restaurant_id from restaurant_tag rt
              join tag t on rt.tag_id = t.id
              where t.slug in (:slugs)
              group by rt.restaurant_id
              having count(distinct rt.tag_id) = :slugCount
            )
          order by r.created_at desc, r.id desc
          """)
  Slice<RestaurantEntity> findAfterFilteredByTags(
      @Param("lastCreatedAt") Instant lastCreatedAt,
      @Param("lastId") UUID lastId,
      @Param("slugs") Collection<String> slugs,
      @Param("slugCount") int slugCount,
      Pageable pageable);
}
