package fr.lepgu.palaisdivin.backend.tag.adapters.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.Projector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class RestaurantTagProjector implements Projector {

  private static final String ATTACH_CYPHER =
      """
      MERGE (r:Restaurant {id: $restaurantId})
      MERGE (t:Tag {id: $tagId})
      SET t.slug = $slug,
          t.category = $category,
          t.label = $label,
          t.labelI18nLocales = $labelI18nLocales,
          t.labelI18nValues = $labelI18nValues
      MERGE (r)-[h:HAS_TAG]->(t)
      SET h.attachedAt = $attachedAt
      """;

  private static final String DETACH_CYPHER =
      """
      MATCH (:Restaurant {id: $restaurantId})-[h:HAS_TAG]->(:Tag {id: $tagId})
      DELETE h
      """;

  private final Neo4jClient neo4jClient;

  RestaurantTagProjector(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public String aggregateType() {
    return "RestaurantTag";
  }

  @Override
  public void project(String eventType, JsonNode payload) {
    switch (eventType) {
      case "RestaurantTagAttached" -> {
        JsonNode i18n = payload.get("tagLabelI18n");
        List<String> locales = new ArrayList<>();
        List<String> values = new ArrayList<>();
        if (i18n != null && i18n.isObject()) {
          i18n.fieldNames()
              .forEachRemaining(
                  k -> {
                    locales.add(k);
                    values.add(i18n.get(k).asText());
                  });
        }
        neo4jClient
            .query(ATTACH_CYPHER)
            .bindAll(
                Map.ofEntries(
                    Map.entry("restaurantId", payload.get("restaurantId").asText()),
                    Map.entry("tagId", payload.get("tagId").asText()),
                    Map.entry("slug", payload.get("tagSlug").asText()),
                    Map.entry("category", payload.get("tagCategory").asText()),
                    Map.entry("label", payload.get("tagLabel").asText()),
                    Map.entry("labelI18nLocales", locales),
                    Map.entry("labelI18nValues", values),
                    Map.entry("attachedAt", payload.get("attachedAt").asText())))
            .run();
      }
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
