package fr.lepgu.palaisdivin.backend.shared.adapters.postgres.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

  Optional<IdempotencyKeyEntity> findFirstByKeyAndUserIdAndAggregateTypeAndCreatedAtAfter(
      String key, UUID userId, String aggregateType, Instant cutoff);
}
