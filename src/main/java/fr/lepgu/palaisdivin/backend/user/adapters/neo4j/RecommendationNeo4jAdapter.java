package fr.lepgu.palaisdivin.backend.user.adapters.neo4j;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class RecommendationNeo4jAdapter implements RecommendationGraphPort {

  private static final String FIRST_PAGE_CYPHER =
      """
      MATCH (me:User {id: $userId})-[:KNOWS*1..2]->(rater:User)-[r:RATED]->(rest:Restaurant)
      WHERE rater.id <> $userId
        AND NOT (me)-[:RATED]->(rest)
      WITH DISTINCT rest, rater, r.score AS score
      WITH rest, sum(score) AS affinity, count(rater) AS recommenderCount
      RETURN rest.id AS id,
             rest.name AS name,
             rest.address AS address,
             rest.latitude AS latitude,
             rest.longitude AS longitude,
             affinity,
             recommenderCount
      ORDER BY affinity DESC, rest.id ASC
      LIMIT $limit
      """;

  private static final String KEYSET_CYPHER =
      """
      MATCH (me:User {id: $userId})-[:KNOWS*1..2]->(rater:User)-[r:RATED]->(rest:Restaurant)
      WHERE rater.id <> $userId
        AND NOT (me)-[:RATED]->(rest)
      WITH DISTINCT rest, rater, r.score AS score
      WITH rest, sum(score) AS affinity, count(rater) AS recommenderCount
      WHERE affinity < $cursorAffinity
         OR (affinity = $cursorAffinity AND rest.id > $cursorId)
      RETURN rest.id AS id,
             rest.name AS name,
             rest.address AS address,
             rest.latitude AS latitude,
             rest.longitude AS longitude,
             affinity,
             recommenderCount
      ORDER BY affinity DESC, rest.id ASC
      LIMIT $limit
      """;

  private static final String AFFINITY_CYPHER =
      """
      MATCH (me:User {id: $userId})-[:KNOWS*1..2]->(rater:User)
            -[r:RATED]->(rest:Restaurant {id: $restaurantId})
      WHERE rater.id <> $userId
      WITH DISTINCT rater, r.score AS score
      WITH sum(score) AS affinity, count(rater) AS recommenderCount
      RETURN affinity, recommenderCount
      """;

  private final Neo4jClient neo4jClient;

  RecommendationNeo4jAdapter(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public CursorPage<Recommendation> findRecommendations(
      UserId requester, RecommendationCursor cursor, int size) {
    int limit = size + 1;
    Collection<Map<String, Object>> rows;
    if (cursor == null) {
      rows =
          neo4jClient
              .query(FIRST_PAGE_CYPHER)
              .bindAll(Map.of("userId", requester.value().toString(), "limit", limit))
              .fetch()
              .all();
    } else {
      Map<String, Object> params = new HashMap<>();
      params.put("userId", requester.value().toString());
      params.put("cursorAffinity", cursor.affinity());
      params.put("cursorId", cursor.id().value().toString());
      params.put("limit", limit);
      rows = neo4jClient.query(KEYSET_CYPHER).bindAll(params).fetch().all();
    }

    List<Recommendation> mapped = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      mapped.add(toDomain(row));
    }
    boolean hasNext = mapped.size() > size;
    if (hasNext) {
      mapped = mapped.subList(0, size);
    }
    return new CursorPage<>(mapped, hasNext);
  }

  @Override
  public RestaurantAffinity findAffinityFor(UserId requester, RestaurantId restaurant) {
    // Cypher aggregations on an empty path still produce one row with sum=0, count=0 —
    // unreachable friend networks naturally surface as a zero-affinity record here.
    Collection<Map<String, Object>> rows =
        neo4jClient
            .query(AFFINITY_CYPHER)
            .bindAll(
                Map.of(
                    "userId", requester.value().toString(),
                    "restaurantId", restaurant.value().toString()))
            .fetch()
            .all();
    if (rows.isEmpty()) {
      return new RestaurantAffinity(restaurant, 0.0, 0);
    }
    Map<String, Object> row = rows.iterator().next();
    return new RestaurantAffinity(
        restaurant,
        ((Number) row.get("affinity")).doubleValue(),
        ((Number) row.get("recommenderCount")).intValue());
  }

  private static Recommendation toDomain(Map<String, Object> row) {
    return new Recommendation(
        new RestaurantId(UUID.fromString((String) row.get("id"))),
        (String) row.get("name"),
        (String) row.get("address"),
        ((Number) row.get("latitude")).doubleValue(),
        ((Number) row.get("longitude")).doubleValue(),
        ((Number) row.get("affinity")).doubleValue(),
        ((Number) row.get("recommenderCount")).intValue());
  }
}
