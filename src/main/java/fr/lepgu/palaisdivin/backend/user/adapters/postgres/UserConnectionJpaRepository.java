package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserConnectionJpaRepository extends JpaRepository<UserConnectionEntity, UUID> {

  Optional<UserConnectionEntity> findBySourceUserIdAndTargetUserId(
      UUID sourceUserId, UUID targetUserId);

  @Query(
      """
      select c from UserConnectionEntity c
      where c.sourceUserId = :sourceId
      order by c.createdAt desc, c.id desc
      """)
  Slice<UserConnectionEntity> findFirstPageBySource(
      @Param("sourceId") UUID sourceId, Pageable pageable);

  @Query(
      """
      select c from UserConnectionEntity c
      where c.sourceUserId = :sourceId
        and (c.createdAt < :lastCreatedAt
             or (c.createdAt = :lastCreatedAt and c.id < :lastId))
      order by c.createdAt desc, c.id desc
      """)
  Slice<UserConnectionEntity> findAfterBySource(
      @Param("sourceId") UUID sourceId,
      @Param("lastCreatedAt") Instant lastCreatedAt,
      @Param("lastId") UUID lastId,
      Pageable pageable);
}
