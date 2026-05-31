package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OutboxPublisherAdapter implements OutboxPublisher {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private final OutboxEventJpaRepository jpa;
  private final Clock clock;

  OutboxPublisherAdapter(OutboxEventJpaRepository jpa, Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
    JsonNode payloadJson = MAPPER.valueToTree(payload);
    OutboxEventEntity entity =
        new OutboxEventEntity(
            UUID.randomUUID(),
            aggregateType,
            aggregateId,
            eventType,
            payloadJson,
            Instant.now(clock));
    jpa.save(entity);
  }
}
