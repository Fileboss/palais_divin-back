package fr.lepgu.palaisdivin.backend.shared.adapters.postgres.idempotency;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.IdempotencyKeyPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyKeyPostgresAdapter implements IdempotencyKeyPort {

  private final IdempotencyKeyJpaRepository jpa;
  private final Clock clock;

  IdempotencyKeyPostgresAdapter(IdempotencyKeyJpaRepository jpa, Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public Optional<UUID> findRecent(String key, UserId userId, String aggregateType, Duration ttl) {
    Instant cutoff = Instant.now(clock).minus(ttl);
    return jpa.findFirstByKeyAndUserIdAndAggregateTypeAndCreatedAtAfter(
            key, userId.value(), aggregateType, cutoff)
        .map(IdempotencyKeyEntity::getAggregateId);
  }

  @Override
  public void record(String key, UserId userId, String aggregateType, UUID aggregateId) {
    jpa.save(
        new IdempotencyKeyEntity(
            UUID.randomUUID(),
            key,
            userId.value(),
            aggregateType,
            aggregateId,
            Instant.now(clock)));
  }
}
