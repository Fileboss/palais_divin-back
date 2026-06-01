package fr.lepgu.palaisdivin.backend.user.adapters.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.user.domain.events.ConnectionCreated;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

class ConnectionProjectorIT extends AbstractIntegrationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Autowired Neo4jClient neo4jClient;
  @Autowired ConnectionProjector projector;

  @BeforeEach
  void clearNeo4j() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
  }

  @Test
  void projectingConnectionCreatesKnowsEdge() {
    UUID connId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
    JsonNode payload =
        MAPPER.valueToTree(new ConnectionCreated(connId, sourceId, targetId, createdAt));

    projector.project("ConnectionCreated", payload);

    Map<String, Object> edge =
        neo4jClient
            .query(
                "MATCH (s:User {id: $sourceId})-[k:KNOWS]->(t:User {id: $targetId})"
                    + " RETURN k.createdAt AS createdAt")
            .bindAll(Map.of("sourceId", sourceId.toString(), "targetId", targetId.toString()))
            .fetch()
            .one()
            .orElse(null);
    assertThat(edge).isNotNull();
    assertThat(edge.get("createdAt")).isEqualTo(createdAt.toString());
  }

  @Test
  void projectingTheSameEventTwiceLeavesExactlyOneKnowsEdge() {
    UUID connId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    JsonNode payload =
        MAPPER.valueToTree(
            new ConnectionCreated(
                connId, sourceId, targetId, Instant.parse("2026-06-01T10:00:00Z")));

    projector.project("ConnectionCreated", payload);
    projector.project("ConnectionCreated", payload);

    Long count =
        neo4jClient
            .query(
                "MATCH (s:User {id: $sourceId})-[k:KNOWS]->(t:User {id: $targetId})"
                    + " RETURN count(k) AS c")
            .bindAll(Map.of("sourceId", sourceId.toString(), "targetId", targetId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void projectingBeforeUserNodesExistCreatesStubNodesAndEdge() {
    UUID connId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    JsonNode payload =
        MAPPER.valueToTree(
            new ConnectionCreated(
                connId, sourceId, targetId, Instant.parse("2026-06-01T10:00:00Z")));

    projector.project("ConnectionCreated", payload);

    Map<String, Object> result =
        neo4jClient
            .query(
                "MATCH (s:User {id: $sourceId})-[k:KNOWS]->(t:User {id: $targetId})"
                    + " RETURN size(keys(s)) AS sourceKeys, size(keys(t)) AS targetKeys")
            .bindAll(Map.of("sourceId", sourceId.toString(), "targetId", targetId.toString()))
            .fetch()
            .one()
            .orElse(null);
    assertThat(result).isNotNull();
    assertThat(((Number) result.get("sourceKeys")).intValue()).isEqualTo(1);
    assertThat(((Number) result.get("targetKeys")).intValue()).isEqualTo(1);
  }

  @Test
  void reverseDirectionCreatesASecondSeparateEdge() {
    UUID connId1 = UUID.randomUUID();
    UUID connId2 = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");

    projector.project(
        "ConnectionCreated",
        MAPPER.valueToTree(new ConnectionCreated(connId1, sourceId, targetId, createdAt)));
    projector.project(
        "ConnectionCreated",
        MAPPER.valueToTree(new ConnectionCreated(connId2, targetId, sourceId, createdAt)));

    Long aToB =
        neo4jClient
            .query(
                "MATCH (s:User {id: $sourceId})-[:KNOWS]->(t:User {id: $targetId})"
                    + " RETURN count(*) AS c")
            .bindAll(Map.of("sourceId", sourceId.toString(), "targetId", targetId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    Long bToA =
        neo4jClient
            .query(
                "MATCH (s:User {id: $sourceId})-[:KNOWS]->(t:User {id: $targetId})"
                    + " RETURN count(*) AS c")
            .bindAll(Map.of("sourceId", targetId.toString(), "targetId", sourceId.toString()))
            .fetchAs(Long.class)
            .one()
            .orElseThrow();
    assertThat(aToB).isEqualTo(1L);
    assertThat(bToA).isEqualTo(1L);
  }
}
