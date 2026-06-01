package fr.lepgu.palaisdivin.backend.user.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class ConnectionProjector implements Projector {

  private static final String MERGE_CYPHER =
      """
      MERGE (s:User {id: $sourceUserId})
      MERGE (t:User {id: $targetUserId})
      MERGE (s)-[k:KNOWS]->(t)
      SET k.createdAt = $createdAt
      """;

  private final Neo4jClient neo4jClient;

  ConnectionProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "Connection";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    neo4jClient
        .query(MERGE_CYPHER)
        .bindAll(
            Map.of(
                "sourceUserId", payload.get("sourceUserId").asText(),
                "targetUserId", payload.get("targetUserId").asText(),
                "createdAt", payload.get("createdAt").asText()))
        .run();
  }
}
