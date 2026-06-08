package fr.lepgu.palaisdivin.backend.restaurant.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListAffinityRankedRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListAffinityRankedRestaurantsService implements ListAffinityRankedRestaurantsUseCase {

  private final UserRepositoryPort users;
  private final RecommendationGraphPort graph;
  private final RestaurantRepositoryPort restaurants;

  public ListAffinityRankedRestaurantsService(
      UserRepositoryPort users,
      RecommendationGraphPort graph,
      RestaurantRepositoryPort restaurants) {
    this.users = users;
    this.graph = graph;
    this.restaurants = restaurants;
  }

  @Override
  public CursorPage<Restaurant> list(
      String requesterSubject, RestaurantCursor.ByAffinity cursor, int size) {
    UserId requester = users.requireBySubject(requesterSubject);
    RecommendationCursor.ByAffinity recoCursor =
        cursor == null
            ? null
            : new RecommendationCursor.ByAffinity(cursor.affinity(), new RestaurantId(cursor.id()));
    CursorPage<Recommendation> recoPage =
        graph.findRecommendations(requester, recoCursor, size, true);
    if (recoPage.data().isEmpty()) {
      return new CursorPage<>(List.of(), recoPage.hasNext());
    }
    List<RestaurantId> ids = recoPage.data().stream().map(Recommendation::restaurantId).toList();
    Map<RestaurantId, Restaurant> byId = restaurants.findByIds(ids);
    List<Restaurant> ordered = new ArrayList<>(recoPage.data().size());
    for (Recommendation reco : recoPage.data()) {
      Restaurant base = byId.get(reco.restaurantId());
      if (base == null) {
        // Restaurant was deleted in Postgres but Neo4j still has the projection — skip.
        continue;
      }
      ordered.add(withAffinity(base, reco.affinity()));
    }
    return new CursorPage<>(ordered, recoPage.hasNext());
  }

  private static Restaurant withAffinity(Restaurant r, double affinity) {
    return new Restaurant(
        r.id(),
        r.name(),
        r.address(),
        r.location(),
        r.createdAt(),
        r.avgRating(),
        r.distanceMetres(),
        affinity);
  }
}
