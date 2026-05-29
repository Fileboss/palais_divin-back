package fr.lepgu.palaisdivin.backend.user.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class UserProjector implements Projector {

  private static final String MERGE_CYPHER =
      """
      MERGE (u:User {id: $id})
      SET u.subject = $subject,
          u.email = $email,
          u.displayName = $displayName,
          u.createdAt = $createdAt
      """;

  private final Neo4jClient neo4jClient;

  UserProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "User";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    neo4jClient
        .query(MERGE_CYPHER)
        .bindAll(
            Map.of(
                "id", payload.get("id").asText(),
                "subject", payload.get("subject").asText(),
                "email", payload.get("email").asText(),
                "displayName", payload.get("displayName").asText(),
                "createdAt", payload.get("createdAt").asText()))
        .run();
  }
}
