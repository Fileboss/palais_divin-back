package fr.lepgu.palaisdivin.backend.user.adapters.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

class RecommendationNeo4jAdapterIT extends AbstractIntegrationTest {

  @Autowired Neo4jClient neo4jClient;
  @Autowired RecommendationNeo4jAdapter adapter;

  private UserId me;

  @BeforeEach
  void setUp() {
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    me = UserId.newId();
    mergeUser(me);
  }

  @Test
  void noFriends_returnsEmptyPage() {
    CursorPage<Recommendation> page = adapter.findRecommendations(me, null, 20, false);

    assertThat(page.data()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void directFriendRatedRestaurant_appearsAtDepth1() {
    UserId friend = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeRestaurant(rest, "Septime", "80 Rue de Charonne", 48.852, 2.380);
    mergeKnows(me, friend);
    mergeRated(friend, rest, 5);

    CursorPage<Recommendation> page = adapter.findRecommendations(me, null, 20, false);

    assertThat(page.data()).hasSize(1);
    Recommendation r = page.data().getFirst();
    assertThat(r.restaurantId()).isEqualTo(rest);
    assertThat(r.name()).isEqualTo("Septime");
    assertThat(r.address()).isEqualTo("80 Rue de Charonne");
    assertThat(r.latitude()).isEqualTo(48.852);
    assertThat(r.longitude()).isEqualTo(2.380);
    assertThat(r.affinity()).isEqualTo(5.0);
    assertThat(r.recommenderCount()).isEqualTo(1);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void friendOfFriendRatedRestaurant_appearsAtDepth2() {
    UserId friend = UserId.newId();
    UserId fof = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeUser(fof);
    mergeRestaurant(rest, "Le Servan", "32 Rue Saint-Maur", 48.860, 2.382);
    mergeKnows(me, friend);
    mergeKnows(friend, fof);
    mergeRated(fof, rest, 4);

    CursorPage<Recommendation> page = adapter.findRecommendations(me, null, 20, false);

    assertThat(page.data()).hasSize(1);
    Recommendation r = page.data().getFirst();
    assertThat(r.restaurantId()).isEqualTo(rest);
    assertThat(r.affinity()).isEqualTo(4.0);
    assertThat(r.recommenderCount()).isEqualTo(1);
  }

  @Test
  void selfRatedRestaurant_isExcluded() {
    UserId friend = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeRestaurant(rest, "Chez Aline", "85 Rue de la Roquette", 48.857, 2.378);
    mergeKnows(me, friend);
    mergeRated(friend, rest, 5);
    mergeRated(me, rest, 3);

    CursorPage<Recommendation> page = adapter.findRecommendations(me, null, 20, false);

    assertThat(page.data()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void selfRatedRestaurant_isIncluded_whenIncludeOwnTrue() {
    UserId friend = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeRestaurant(rest, "Chez Aline", "85 Rue de la Roquette", 48.857, 2.378);
    mergeKnows(me, friend);
    mergeRated(friend, rest, 5);
    mergeRated(me, rest, 3);

    CursorPage<Recommendation> page = adapter.findRecommendations(me, null, 20, true);

    assertThat(page.data()).hasSize(1);
    Recommendation r = page.data().getFirst();
    assertThat(r.restaurantId()).isEqualTo(rest);
    // `rater.id <> $userId` keeps the self-rating out of the affinity sum even when
    // includeOwn=true.
    assertThat(r.affinity()).isEqualTo(5.0);
    assertThat(r.recommenderCount()).isEqualTo(1);
  }

  @Test
  void walkPagesNoOverlap_visitsEveryRestaurantOnce_whenIncludeOwnTrue() {
    UserId friend = UserId.newId();
    mergeUser(friend);
    mergeKnows(me, friend);

    RestaurantId r5 = RestaurantId.newId();
    RestaurantId r4 = RestaurantId.newId();
    RestaurantId r3 = RestaurantId.newId();
    RestaurantId r2 = RestaurantId.newId();
    RestaurantId r1 = RestaurantId.newId();
    mergeRestaurant(r5, "A", "addr A", 48.8, 2.3);
    mergeRestaurant(r4, "B", "addr B", 48.8, 2.3);
    mergeRestaurant(r3, "C", "addr C", 48.8, 2.3);
    mergeRestaurant(r2, "D", "addr D", 48.8, 2.3);
    mergeRestaurant(r1, "E", "addr E", 48.8, 2.3);
    mergeRated(friend, r5, 5);
    mergeRated(friend, r4, 4);
    mergeRated(friend, r3, 3);
    mergeRated(friend, r2, 2);
    mergeRated(friend, r1, 1);
    // Self-rate two of them — they must still appear in the includeOwn walk.
    mergeRated(me, r4, 2);
    mergeRated(me, r2, 1);

    CursorPage<Recommendation> page1 = adapter.findRecommendations(me, null, 2, true);
    assertThat(page1.data()).hasSize(2);
    assertThat(page1.hasNext()).isTrue();
    assertThat(page1.data().get(0).affinity()).isEqualTo(5.0);
    assertThat(page1.data().get(1).affinity()).isEqualTo(4.0);

    RecommendationCursor.ByAffinity cursor1 =
        new RecommendationCursor.ByAffinity(
            page1.data().getLast().affinity(), page1.data().getLast().restaurantId());

    CursorPage<Recommendation> page2 = adapter.findRecommendations(me, cursor1, 2, true);
    assertThat(page2.data()).hasSize(2);
    assertThat(page2.hasNext()).isTrue();
    assertThat(page2.data().get(0).affinity()).isEqualTo(3.0);
    assertThat(page2.data().get(1).affinity()).isEqualTo(2.0);

    RecommendationCursor.ByAffinity cursor2 =
        new RecommendationCursor.ByAffinity(
            page2.data().getLast().affinity(), page2.data().getLast().restaurantId());

    CursorPage<Recommendation> page3 = adapter.findRecommendations(me, cursor2, 2, true);
    assertThat(page3.data()).hasSize(1);
    assertThat(page3.hasNext()).isFalse();
    assertThat(page3.data().getFirst().affinity()).isEqualTo(1.0);
  }

  @Test
  void multipleRaters_sumScoresAndCountRecommenders() {
    UserId friend = UserId.newId();
    UserId fof = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeUser(fof);
    mergeRestaurant(rest, "Clamato", "80 Rue de Charonne", 48.852, 2.379);
    mergeKnows(me, friend);
    mergeKnows(friend, fof);
    mergeRated(friend, rest, 5);
    mergeRated(fof, rest, 3);

    CursorPage<Recommendation> page = adapter.findRecommendations(me, null, 20, false);

    assertThat(page.data()).hasSize(1);
    Recommendation r = page.data().getFirst();
    assertThat(r.affinity()).isEqualTo(8.0);
    assertThat(r.recommenderCount()).isEqualTo(2);
  }

  @Test
  void walkPagesNoOverlap_visitsEveryRestaurantOnce() {
    UserId friend = UserId.newId();
    mergeUser(friend);
    mergeKnows(me, friend);

    // 5 restaurants with descending scores so order is deterministic across pages.
    RestaurantId r5 = RestaurantId.newId();
    RestaurantId r4 = RestaurantId.newId();
    RestaurantId r3 = RestaurantId.newId();
    RestaurantId r2 = RestaurantId.newId();
    RestaurantId r1 = RestaurantId.newId();
    mergeRestaurant(r5, "A", "addr A", 48.8, 2.3);
    mergeRestaurant(r4, "B", "addr B", 48.8, 2.3);
    mergeRestaurant(r3, "C", "addr C", 48.8, 2.3);
    mergeRestaurant(r2, "D", "addr D", 48.8, 2.3);
    mergeRestaurant(r1, "E", "addr E", 48.8, 2.3);
    mergeRated(friend, r5, 5);
    mergeRated(friend, r4, 4);
    mergeRated(friend, r3, 3);
    mergeRated(friend, r2, 2);
    mergeRated(friend, r1, 1);

    CursorPage<Recommendation> page1 = adapter.findRecommendations(me, null, 2, false);
    assertThat(page1.data()).hasSize(2);
    assertThat(page1.hasNext()).isTrue();
    assertThat(page1.data().get(0).affinity()).isEqualTo(5.0);
    assertThat(page1.data().get(1).affinity()).isEqualTo(4.0);

    RecommendationCursor.ByAffinity cursor1 =
        new RecommendationCursor.ByAffinity(
            page1.data().getLast().affinity(), page1.data().getLast().restaurantId());

    CursorPage<Recommendation> page2 = adapter.findRecommendations(me, cursor1, 2, false);
    assertThat(page2.data()).hasSize(2);
    assertThat(page2.hasNext()).isTrue();
    assertThat(page2.data().get(0).affinity()).isEqualTo(3.0);
    assertThat(page2.data().get(1).affinity()).isEqualTo(2.0);

    RecommendationCursor.ByAffinity cursor2 =
        new RecommendationCursor.ByAffinity(
            page2.data().getLast().affinity(), page2.data().getLast().restaurantId());

    CursorPage<Recommendation> page3 = adapter.findRecommendations(me, cursor2, 2, false);
    assertThat(page3.data()).hasSize(1);
    assertThat(page3.hasNext()).isFalse();
    assertThat(page3.data().getFirst().affinity()).isEqualTo(1.0);
  }

  // --- affinity (M7.3) ---------------------------------------------------

  @Test
  void affinity_restaurantNotInGraph_returnsZero() {
    RestaurantId rest = RestaurantId.newId();

    RestaurantAffinity result = adapter.findAffinityFor(me, rest);

    assertThat(result.restaurantId()).isEqualTo(rest);
    assertThat(result.affinity()).isZero();
    assertThat(result.recommenderCount()).isZero();
  }

  @Test
  void affinity_noFriends_returnsZero() {
    RestaurantId rest = RestaurantId.newId();
    mergeRestaurant(rest, "Septime", "80 Rue de Charonne", 48.852, 2.380);

    RestaurantAffinity result = adapter.findAffinityFor(me, rest);

    assertThat(result.affinity()).isZero();
    assertThat(result.recommenderCount()).isZero();
  }

  @Test
  void affinity_directFriendRated_returnsDepth1Score() {
    UserId friend = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeRestaurant(rest, "Septime", "80 Rue de Charonne", 48.852, 2.380);
    mergeKnows(me, friend);
    mergeRated(friend, rest, 5);

    RestaurantAffinity result = adapter.findAffinityFor(me, rest);

    assertThat(result.restaurantId()).isEqualTo(rest);
    assertThat(result.affinity()).isEqualTo(5.0);
    assertThat(result.recommenderCount()).isEqualTo(1);
  }

  @Test
  void affinity_friendOfFriendRated_returnsDepth2Score() {
    UserId friend = UserId.newId();
    UserId fof = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeUser(fof);
    mergeRestaurant(rest, "Le Servan", "32 Rue Saint-Maur", 48.860, 2.382);
    mergeKnows(me, friend);
    mergeKnows(friend, fof);
    mergeRated(fof, rest, 4);

    RestaurantAffinity result = adapter.findAffinityFor(me, rest);

    assertThat(result.affinity()).isEqualTo(4.0);
    assertThat(result.recommenderCount()).isEqualTo(1);
  }

  @Test
  void affinity_multipleRaters_sumScoresAndCountRecommenders() {
    UserId friend = UserId.newId();
    UserId fof = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeUser(fof);
    mergeRestaurant(rest, "Clamato", "80 Rue de Charonne", 48.852, 2.379);
    mergeKnows(me, friend);
    mergeKnows(friend, fof);
    mergeRated(friend, rest, 5);
    mergeRated(fof, rest, 3);

    RestaurantAffinity result = adapter.findAffinityFor(me, rest);

    assertThat(result.affinity()).isEqualTo(8.0);
    assertThat(result.recommenderCount()).isEqualTo(2);
  }

  @Test
  void affinity_requesterSelfRated_stillComputes() {
    // Diverges from M7.2 recommendations Cypher: affinity is a reflective query — we still want
    // the friend-network score even when the user has already rated the place themselves.
    UserId friend = UserId.newId();
    RestaurantId rest = RestaurantId.newId();
    mergeUser(friend);
    mergeRestaurant(rest, "Chez Aline", "85 Rue de la Roquette", 48.857, 2.378);
    mergeKnows(me, friend);
    mergeRated(friend, rest, 5);
    mergeRated(me, rest, 3);

    RestaurantAffinity result = adapter.findAffinityFor(me, rest);

    assertThat(result.affinity()).isEqualTo(5.0);
    assertThat(result.recommenderCount()).isEqualTo(1);
  }

  // --- helpers -----------------------------------------------------------

  private void mergeUser(UserId id) {
    neo4jClient
        .query("MERGE (u:User {id: $id})")
        .bindAll(Map.of("id", id.value().toString()))
        .run();
  }

  private void mergeRestaurant(
      RestaurantId id, String name, String address, double latitude, double longitude) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("id", id.value().toString());
    params.put("name", name);
    params.put("address", address);
    params.put("latitude", latitude);
    params.put("longitude", longitude);
    neo4jClient
        .query(
            """
            MERGE (r:Restaurant {id: $id})
            SET r.name = $name,
                r.address = $address,
                r.latitude = $latitude,
                r.longitude = $longitude
            """)
        .bindAll(params)
        .run();
  }

  private void mergeKnows(UserId source, UserId target) {
    neo4jClient
        .query(
            """
            MATCH (s:User {id: $s})
            MATCH (t:User {id: $t})
            MERGE (s)-[:KNOWS]->(t)
            """)
        .bindAll(Map.of("s", source.value().toString(), "t", target.value().toString()))
        .run();
  }

  private void mergeRated(UserId user, RestaurantId restaurant, int score) {
    neo4jClient
        .query(
            """
            MATCH (u:User {id: $u})
            MATCH (r:Restaurant {id: $r})
            MERGE (u)-[rated:RATED]->(r)
            SET rated.score = $score, rated.reviewId = $reviewId
            """)
        .bindAll(
            Map.of(
                "u",
                user.value().toString(),
                "r",
                restaurant.value().toString(),
                "score",
                score,
                "reviewId",
                UUID.randomUUID().toString()))
        .run();
  }
}
