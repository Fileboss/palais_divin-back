package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import java.time.Instant;
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
}
