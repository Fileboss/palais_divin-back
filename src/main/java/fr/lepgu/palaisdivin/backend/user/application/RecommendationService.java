package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRecommendationsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecommendationService implements GetRecommendationsUseCase {

  private final UserRepositoryPort users;
  private final RecommendationGraphPort graph;
  private final RestaurantRepositoryPort restaurants;

  public RecommendationService(
      UserRepositoryPort users,
      RecommendationGraphPort graph,
      RestaurantRepositoryPort restaurants) {
    this.users = users;
    this.graph = graph;
    this.restaurants = restaurants;
  }

  @Override
  public CursorPage<Recommendation> list(
      String requesterSubject,
      RecommendationCursor cursor,
      int size,
      boolean includeOwn,
      RecommendationSort sort,
      Coordinates anchor) {
    UserId requester = users.requireBySubject(requesterSubject);
    if (sort == RecommendationSort.AFFINITY_DESC) {
      RecommendationCursor.ByAffinity affinityCursor = (RecommendationCursor.ByAffinity) cursor;
      return graph.findRecommendations(requester, affinityCursor, size, includeOwn);
    }

    Map<RestaurantId, RestaurantAffinity> affinityMap =
        graph.findAllRecommendedAffinities(requester, includeOwn);
    if (affinityMap.isEmpty()) {
      return new CursorPage<>(List.of(), false);
    }

    RestaurantFilter filter =
        new RestaurantFilter(List.of(), null, anchor, affinityMap.keySet());
    RestaurantCursor restaurantCursor = toRestaurantCursor(cursor);
    RestaurantSort restaurantSort = toRestaurantSort(sort);
    CursorPage<Restaurant> page = restaurants.findAll(restaurantCursor, size, filter, restaurantSort);

    List<Recommendation> enriched =
        page.data().stream().map(r -> enrich(r, affinityMap.get(r.id()))).toList();
    return new CursorPage<>(enriched, page.hasNext());
  }

  private static Recommendation enrich(Restaurant r, RestaurantAffinity affinity) {
    return new Recommendation(
        r.id(),
        r.name(),
        r.address(),
        r.location().latitude(),
        r.location().longitude(),
        affinity.affinity(),
        affinity.recommenderCount(),
        r.avgRating(),
        r.distanceMetres(),
        r.createdAt());
  }

  private static RestaurantCursor toRestaurantCursor(RecommendationCursor cursor) {
    if (cursor == null) {
      return null;
    }
    return switch (cursor) {
      case RecommendationCursor.ByAffinity c ->
          throw new IllegalStateException("ByAffinity cursor is handled in graph branch");
      case RecommendationCursor.ByRating c ->
          new RestaurantCursor.ByRating(c.avgRating(), c.id().value());
      case RecommendationCursor.ByName c -> new RestaurantCursor.ByName(c.name(), c.id().value());
      case RecommendationCursor.ByDistance c ->
          new RestaurantCursor.ByDistance(c.distanceMetres(), c.id().value());
      case RecommendationCursor.ByCreatedAt c ->
          new RestaurantCursor.ByCreatedAt(c.createdAt(), c.id().value());
    };
  }

  private static RestaurantSort toRestaurantSort(RecommendationSort sort) {
    return switch (sort) {
      case AFFINITY_DESC ->
          throw new IllegalStateException("AFFINITY_DESC is handled in graph branch");
      case RATING_DESC -> RestaurantSort.RATING_DESC;
      case NAME_ASC -> RestaurantSort.NAME_ASC;
      case DISTANCE_ASC -> RestaurantSort.DISTANCE_ASC;
      case CREATED_AT_DESC -> RestaurantSort.CREATED_AT_DESC;
    };
  }
}
