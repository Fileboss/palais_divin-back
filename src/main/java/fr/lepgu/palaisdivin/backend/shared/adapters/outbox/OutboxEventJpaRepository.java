package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {}
