package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import com.fasterxml.jackson.databind.JsonNode;

public interface Projector {

  String aggregateType();

  void project(String eventType, JsonNode payload);
}
