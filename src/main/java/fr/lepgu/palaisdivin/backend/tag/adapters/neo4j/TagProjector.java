package fr.lepgu.palaisdivin.backend.tag.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class TagProjector implements Projector {

  private static final String ATTACH_CYPHER =
      """
      MERGE (r:Restaurant {id: $restaurantId})
      MERGE (t:Tag {id: $tagId})
      SET t.slug = $slug,
          t.category = $category,
          t.label = $label
      MERGE (r)-[h:HAS_TAG]->(t)
      SET h.attachedAt = $attachedAt
      """;

  private static final String DETACH_CYPHER =
      """
      MATCH (:Restaurant {id: $restaurantId})-[h:HAS_TAG]->(:Tag {id: $tagId})
      DELETE h
      """;

  private final Neo4jClient neo4jClient;

  TagProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "RestaurantTag";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    switch (eventType) {
      case "RestaurantTagAttached" ->
          neo4jClient
              .query(ATTACH_CYPHER)
              .bindAll(
                  Map.of(
                      "restaurantId", payload.get("restaurantId").asText(),
                      "tagId", payload.get("tagId").asText(),
                      "slug", payload.get("tagSlug").asText(),
                      "category", payload.get("tagCategory").asText(),
                      "label", payload.get("tagLabel").asText(),
                      "attachedAt", payload.get("attachedAt").asText()))
              .run();
      case "RestaurantTagDetached" ->
          neo4jClient
              .query(DETACH_CYPHER)
              .bindAll(
                  Map.of(
                      "restaurantId", payload.get("restaurantId").asText(),
                      "tagId", payload.get("tagId").asText()))
              .run();
      default ->
          throw new IllegalArgumentException("Unknown RestaurantTag event type: " + eventType);
    }
  }
}
