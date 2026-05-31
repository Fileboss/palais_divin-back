package fr.lepgu.palaisdivin.backend.shared.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyPort {

  Optional<UUID> findRecent(String key, UserId userId, String aggregateType, Duration ttl);

  void record(String key, UserId userId, String aggregateType, UUID aggregateId);
}
