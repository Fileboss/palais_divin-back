package fr.lepgu.palaisdivin.backend.restaurant.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class RestaurantProjector implements Projector {

  private static final String MERGE_CYPHER =
      """
      MERGE (r:Restaurant {id: $id})
      SET r.name = $name,
          r.address = $address,
          r.latitude = $latitude,
          r.longitude = $longitude,
          r.createdAt = $createdAt
      """;

  private final Neo4jClient neo4jClient;

  RestaurantProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "Restaurant";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    neo4jClient
        .query(MERGE_CYPHER)
        .bindAll(
            Map.of(
                "id", payload.get("id").asText(),
                "name", payload.get("name").asText(),
                "address", payload.get("address").asText(),
                "latitude", payload.get("latitude").asDouble(),
                "longitude", payload.get("longitude").asDouble(),
                "createdAt", payload.get("createdAt").asText()))
        .run();
  }
}
