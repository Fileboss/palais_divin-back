package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

  @Query(
      "SELECT e FROM OutboxEventEntity e WHERE e.status = 'PENDING' "
          + "ORDER BY e.createdAt ASC, e.id ASC")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  List<OutboxEventEntity> findPendingForUpdateSkipLocked(Limit limit);
}
