package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ExpandTagSlugsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRecommendationsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecommendationService implements GetRecommendationsUseCase {

  private static final int AFFINITY_FILTER_MAX_CANDIDATES = 5000;

  private final UserRepositoryPort users;
  private final RecommendationGraphPort graph;
  private final RestaurantRepositoryPort restaurants;
  private final ExpandTagSlugsUseCase expandTagSlugs;

  public RecommendationService(
      UserRepositoryPort users,
      RecommendationGraphPort graph,
      RestaurantRepositoryPort restaurants,
      ExpandTagSlugsUseCase expandTagSlugs) {
    this.users = users;
    this.graph = graph;
    this.restaurants = restaurants;
    this.expandTagSlugs = expandTagSlugs;
  }

  @Override
  public CursorPage<Recommendation> list(
      String requesterSubject,
      RecommendationCursor cursor,
      int size,
      boolean includeOwn,
      RecommendationSort sort,
      Coordinates anchor,
      RestaurantFilter filter) {
    UserId requester = users.requireBySubject(requesterSubject);
    boolean hasUserFilter = hasUserFilter(filter);

    if (sort == RecommendationSort.AFFINITY_DESC && !hasUserFilter) {
      RecommendationCursor.ByAffinity affinityCursor = (RecommendationCursor.ByAffinity) cursor;
      return graph.findRecommendations(requester, affinityCursor, size, includeOwn);
    }

    Map<RestaurantId, RestaurantAffinity> affinityMap =
        graph.findAllRecommendedAffinities(requester, includeOwn);
    if (affinityMap.isEmpty()) {
      return new CursorPage<>(List.of(), false);
    }

    RestaurantFilter merged = mergeFilter(filter, anchor, affinityMap.keySet());

    if (sort == RecommendationSort.AFFINITY_DESC) {
      return inMemoryAffinitySlice(
          merged, affinityMap, (RecommendationCursor.ByAffinity) cursor, size);
    }

    RestaurantCursor restaurantCursor = toRestaurantCursor(cursor);
    RestaurantSort restaurantSort = toRestaurantSort(sort);
    CursorPage<Restaurant> page =
        restaurants.findAll(restaurantCursor, size, merged, restaurantSort);

    List<Recommendation> enriched =
        page.data().stream().map(r -> enrich(r, affinityMap.get(r.id()))).toList();
    return new CursorPage<>(enriched, page.hasNext());
  }

  private CursorPage<Recommendation> inMemoryAffinitySlice(
      RestaurantFilter filter,
      Map<RestaurantId, RestaurantAffinity> affinityMap,
      RecommendationCursor.ByAffinity cursor,
      int size) {
    CursorPage<Restaurant> candidates =
        restaurants.findAll(
            null, AFFINITY_FILTER_MAX_CANDIDATES, filter, RestaurantSort.CREATED_AT_DESC);
    List<Restaurant> sorted = new ArrayList<>(candidates.data());
    sorted.sort(
        Comparator.comparingDouble((Restaurant r) -> affinityMap.get(r.id()).affinity())
            .reversed()
            .thenComparing(r -> r.id().value()));

    int startIndex = 0;
    if (cursor != null) {
      for (int i = 0; i < sorted.size(); i++) {
        Restaurant r = sorted.get(i);
        double a = affinityMap.get(r.id()).affinity();
        boolean past =
            a < cursor.affinity()
                || (a == cursor.affinity() && r.id().value().compareTo(cursor.id().value()) > 0);
        if (past) {
          startIndex = i;
          break;
        }
        startIndex = i + 1;
      }
    }

    int endIndex = Math.min(startIndex + size, sorted.size());
    List<Recommendation> data =
        sorted.subList(startIndex, endIndex).stream()
            .map(r -> enrich(r, affinityMap.get(r.id())))
            .toList();
    boolean hasNext = endIndex < sorted.size();
    return new CursorPage<>(data, hasNext);
  }

  private RestaurantFilter mergeFilter(
      RestaurantFilter caller, Coordinates anchor, Set<RestaurantId> idsAllowList) {
    if (caller == null) {
      return new RestaurantFilter(List.of(), null, anchor, idsAllowList, null, null, null);
    }
    List<List<String>> expandedGroups = expandTagGroups(caller.tagSlugGroups());
    return new RestaurantFilter(
        expandedGroups,
        caller.name(),
        anchor,
        idsAllowList,
        caller.dineIn(),
        caller.takeOut(),
        caller.delivery());
  }

  private List<List<String>> expandTagGroups(List<List<String>> groups) {
    if (groups.isEmpty()) {
      return List.of();
    }
    Set<String> distinctSlugs = new LinkedHashSet<>();
    for (List<String> group : groups) {
      distinctSlugs.addAll(group);
    }
    Map<String, Set<String>> expansion = expandTagSlugs.expand(distinctSlugs);
    boolean changed = expansion.values().stream().anyMatch(s -> s.size() > 1);
    if (!changed) {
      return groups;
    }
    List<List<String>> expanded = new ArrayList<>(groups.size());
    for (List<String> group : groups) {
      Set<String> union = new LinkedHashSet<>();
      for (String slug : group) {
        union.addAll(expansion.getOrDefault(slug, Set.of(slug)));
      }
      expanded.add(List.copyOf(union));
    }
    return expanded;
  }

  private static boolean hasUserFilter(RestaurantFilter filter) {
    if (filter == null) {
      return false;
    }
    return filter.hasTags()
        || filter.hasName()
        || filter.hasDineIn()
        || filter.hasTakeOut()
        || filter.hasDelivery();
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
