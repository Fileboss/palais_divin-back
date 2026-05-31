package fr.lepgu.palaisdivin.backend.review.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class ReviewProjector implements Projector {

  private static final String MERGE_CYPHER =
      """
      MERGE (u:User {id: $authorId})
      MERGE (r:Restaurant {id: $restaurantId})
      MERGE (u)-[rated:RATED]->(r)
      SET rated.score = $score,
          rated.reviewId = $reviewId,
          rated.createdAt = $createdAt
      """;

  private final Neo4jClient neo4jClient;

  ReviewProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "Review";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    neo4jClient
        .query(MERGE_CYPHER)
        .bindAll(
            Map.of(
                "authorId", payload.get("authorId").asText(),
                "restaurantId", payload.get("restaurantId").asText(),
                "score", payload.get("rating").asInt(),
                "reviewId", payload.get("id").asText(),
                "createdAt", payload.get("createdAt").asText()))
        .run();
  }
}
