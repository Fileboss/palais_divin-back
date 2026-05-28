package fr.lepgu.palaisdivin.backend.shared.domain.ports;

import java.util.UUID;

public interface OutboxPublisher {

  void publish(String aggregateType, UUID aggregateId, String eventType, Object payload);
}
