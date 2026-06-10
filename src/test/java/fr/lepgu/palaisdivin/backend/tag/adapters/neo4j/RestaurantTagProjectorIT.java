package fr.lepgu.palaisdivin.backend.tag.adapters.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.tag.domain.events.RestaurantTagAttached;
import fr.lepgu.palaisdivin.backend.tag.domain.events.RestaurantTagDetached;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

class RestaurantTagProjectorIT extends AbstractIntegrationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Autowired Neo4jClient neo4jClient;
  @Autowired RestaurantTagProjector projector;

  @BeforeEach
  void clearNeo4j() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void attach_event_creates_has_tag_edge_and_tag_node_fields() {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    UUID attachedBy = UUID.randomUUID();
    Instant attachedAt = Instant.parse("2026-06-03T10:00:00Z");
    JsonNode payload =
        MAPPER.valueToTree(
            new RestaurantTagAttached(
                restaurantId,
                tagId,
                "natural-wine",
                "SPECIALTY",
                "Natural wine",
                Map.of(),
                attachedBy,
                attachedAt));

    projector.project("RestaurantTagAttached", payload);

    Map<String, Object> tag =
        neo4jClient
            .query(
                "MATCH (:Restaurant {id: $r})-[h:HAS_TAG]->(t:Tag {id: $t})"
                    + " RETURN t.slug AS slug, t.category AS category, t.label AS label,"
                    + " t.labelI18nLocales AS locales, t.labelI18nValues AS values,"
                    + " h.attachedAt AS attachedAt")
            .bindAll(Map.of("r", restaurantId.toString(), "t", tagId.toString()))
            .fetch()
            .one()
            .orElse(null);
    assertThat(tag).isNotNull();
    assertThat(tag).containsEntry("slug", "natural-wine");
    assertThat(tag).containsEntry("category", "SPECIALTY");
    assertThat(tag).containsEntry("label", "Natural wine");
    assertThat((Iterable<?>) tag.get("locales")).isEmpty();
    assertThat((Iterable<?>) tag.get("values")).isEmpty();
    assertThat(tag).containsEntry("attachedAt", attachedAt.toString());
  }

  @Test
  void attach_event_stores_labelI18n_as_parallel_arrays_on_tag_node() {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    Instant attachedAt = Instant.parse("2026-06-03T10:00:00Z");
    JsonNode payload =
        MAPPER.valueToTree(
            new RestaurantTagAttached(
                restaurantId,
                tagId,
                "vegan",
                "REGIME",
                "Végétalien",
                new java.util.LinkedHashMap<>(Map.of("en", "Vegan", "es", "Vegano", "de", "Vegan")),
                UUID.randomUUID(),
                attachedAt));

    projector.project("RestaurantTagAttached", payload);

    Map<String, Object> tag =
        neo4jClient
            .query(
                "MATCH (t:Tag {id: $t})"
                    + " RETURN t.labelI18nLocales AS locales, t.labelI18nValues AS values")
            .bindAll(Map.of("t", tagId.toString()))
            .fetch()
            .one()
            .orElseThrow();
    @SuppressWarnings("unchecked")
    java.util.List<String> locales = (java.util.List<String>) tag.get("locales");
    @SuppressWarnings("unchecked")
    java.util.List<String> values = (java.util.List<String>) tag.get("values");
    assertThat(locales).hasSize(3);
    assertThat(values).hasSize(3);
    java.util.Map<String, String> reassembled = new java.util.HashMap<>();
    for (int i = 0; i < locales.size(); i++) {
      reassembled.put(locales.get(i), values.get(i));
    }
    assertThat(reassembled)
        .containsEntry("en", "Vegan")
        .containsEntry("es", "Vegano")
        .containsEntry("de", "Vegan");
  }

  @Test
  void detach_event_removes_edge_keeps_tag_node() {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    UUID attachedBy = UUID.randomUUID();
    Instant attachedAt = Instant.parse("2026-06-03T10:00:00Z");
    projector.project(
        "RestaurantTagAttached",
        MAPPER.valueToTree(
            new RestaurantTagAttached(
                restaurantId,
                tagId,
                "vegan",
                "REGIME",
                "Vegan",
                Map.of(),
                attachedBy,
                attachedAt)));

    projector.project(
        "RestaurantTagDetached",
        MAPPER.valueToTree(new RestaurantTagDetached(restaurantId, tagId, attachedAt)));

    Long edgeCount =
        neo4jClient
            .query("MATCH (:Restaurant {id: $r})-[h:HAS_TAG]->(:Tag {id: $t}) RETURN count(h) AS c")
            .bindAll(Map.of("r", restaurantId.toString(), "t", tagId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    Long tagCount =
        neo4jClient
            .query("MATCH (t:Tag {id: $t}) RETURN count(t) AS c")
            .bindAll(Map.of("t", tagId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    assertThat(edgeCount).isZero();
    assertThat(tagCount).isEqualTo(1L);
  }

  @Test
  void attach_event_projected_twice_leaves_one_edge() {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    JsonNode payload =
        MAPPER.valueToTree(
            new RestaurantTagAttached(
                restaurantId,
                tagId,
                "burger",
                "SPECIALTY",
                "Burger",
                Map.of(),
                UUID.randomUUID(),
                Instant.parse("2026-06-03T10:00:00Z")));

    projector.project("RestaurantTagAttached", payload);
    projector.project("RestaurantTagAttached", payload);

    Long edgeCount =
        neo4jClient
            .query("MATCH (:Restaurant {id: $r})-[h:HAS_TAG]->(:Tag {id: $t}) RETURN count(h) AS c")
            .bindAll(Map.of("r", restaurantId.toString(), "t", tagId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    assertThat(edgeCount).isEqualTo(1L);
  }
}
