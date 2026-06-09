package fr.lepgu.palaisdivin.backend.tag.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class TagProjector implements Projector {

  private static final String DELETE_CYPHER =
      """
      MATCH (t:Tag {id: $id})
      DETACH DELETE t
      """;

  private final Neo4jClient neo4jClient;

  TagProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "Tag";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    switch (eventType) {
      case "TagDeleted" ->
          neo4jClient.query(DELETE_CYPHER).bindAll(Map.of("id", payload.get("id").asText())).run();
      default -> throw new IllegalArgumentException("Unknown Tag event type: " + eventType);
    }
  }
}
