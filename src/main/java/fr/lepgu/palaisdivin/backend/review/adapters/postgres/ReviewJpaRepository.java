package fr.lepgu.palaisdivin.backend.review.adapters.postgres;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

  Optional<ReviewEntity> findByRestaurantIdAndAuthorId(UUID restaurantId, UUID authorId);

  long countByRestaurantId(UUID restaurantId);

  @Query(
      """
      select r from ReviewEntity r
      where r.restaurantId = :restaurantId
      order by r.createdAt desc, r.id desc
      """)
  Slice<ReviewEntity> findFirstPageByRestaurant(
      @Param("restaurantId") UUID restaurantId, Pageable pageable);

  @Query(
      """
      select r from ReviewEntity r
      where r.restaurantId = :restaurantId
        and (r.createdAt < :lastCreatedAt
             or (r.createdAt = :lastCreatedAt and r.id < :lastId))
      order by r.createdAt desc, r.id desc
      """)
  Slice<ReviewEntity> findAfterByRestaurant(
      @Param("restaurantId") UUID restaurantId,
      @Param("lastCreatedAt") Instant lastCreatedAt,
      @Param("lastId") UUID lastId,
      Pageable pageable);

  @Query(
      """
      select r from ReviewEntity r
      where r.authorId = :authorId
      order by r.createdAt desc, r.id desc
      """)
  Slice<ReviewEntity> findFirstPageByAuthor(@Param("authorId") UUID authorId, Pageable pageable);

  @Query(
      """
      select r from ReviewEntity r
      where r.authorId = :authorId
        and (r.createdAt < :lastCreatedAt
             or (r.createdAt = :lastCreatedAt and r.id < :lastId))
      order by r.createdAt desc, r.id desc
      """)
  Slice<ReviewEntity> findAfterByAuthor(
      @Param("authorId") UUID authorId,
      @Param("lastCreatedAt") Instant lastCreatedAt,
      @Param("lastId") UUID lastId,
      Pageable pageable);
}
