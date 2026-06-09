package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  private static final String SUBJECT = "kc-subject-abc";

  @Mock UserRepositoryPort users;
  @Mock RecommendationGraphPort graph;
  @Mock RestaurantRepositoryPort restaurants;
  @Mock ExpandTagSlugsUseCase expandTagSlugs;

  RecommendationService service;

  UserId requesterId;
  User requester;

  @BeforeEach
  void setUp() {
    service = new RecommendationService(users, graph, restaurants, expandTagSlugs);
    requesterId = UserId.newId();
    requester =
        new User(
            requesterId, SUBJECT, "me@example.com", "Me", Instant.parse("2026-05-01T00:00:00Z"));
  }

  @Test
  void affinitySort_delegatesToGraph() {
    Recommendation reco =
        new Recommendation(
            RestaurantId.newId(), "Septime", "80 rue de Charonne", 48.8, 2.3, 9.0, 2);
    CursorPage<Recommendation> expected = new CursorPage<>(List.of(reco), false);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(false))).thenReturn(expected);

    CursorPage<Recommendation> result =
        service.list(
            SUBJECT,
            null,
            20,
            false,
            RecommendationSort.AFFINITY_DESC,
            null,
            RestaurantFilter.none());

    assertThat(result).isSameAs(expected);
    verify(graph).findRecommendations(requesterId, null, 20, false);
    verifyNoInteractions(restaurants);
  }

  @Test
  void affinitySort_passesCursorThrough() {
    RecommendationCursor.ByAffinity cursor =
        new RecommendationCursor.ByAffinity(7.5, RestaurantId.newId());
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(requesterId, cursor, 5, false))
        .thenReturn(new CursorPage<>(List.of(), false));

    service.list(
        SUBJECT, cursor, 5, false, RecommendationSort.AFFINITY_DESC, null, RestaurantFilter.none());

    verify(graph).findRecommendations(requesterId, cursor, 5, false);
  }

  @Test
  void ratingSort_pullsIdsFromGraphAndPaginatesViaPostgres() {
    RestaurantId rid1 = RestaurantId.newId();
    RestaurantId rid2 = RestaurantId.newId();
    Restaurant r1 =
        new Restaurant(
            rid1,
            "A",
            "addr-a",
            new Coordinates(48.8, 2.3),
            Instant.parse("2026-05-01T00:00:00Z"),
            4.5,
            null);
    Restaurant r2 =
        new Restaurant(
            rid2,
            "B",
            "addr-b",
            new Coordinates(48.8, 2.3),
            Instant.parse("2026-05-02T00:00:00Z"),
            3.0,
            null);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findAllRecommendedAffinities(requesterId, false))
        .thenReturn(
            Map.of(
                rid1, new RestaurantAffinity(rid1, 9.0, 2),
                rid2, new RestaurantAffinity(rid2, 5.0, 1)));
    when(restaurants.findAll(any(), eq(20), any(), eq(RestaurantSort.RATING_DESC)))
        .thenReturn(new CursorPage<>(List.of(r1, r2), false));

    CursorPage<Recommendation> result =
        service.list(
            SUBJECT,
            null,
            20,
            false,
            RecommendationSort.RATING_DESC,
            null,
            RestaurantFilter.none());

    assertThat(result.data()).hasSize(2);
    assertThat(result.data().get(0).avgRating()).isEqualTo(4.5);
    assertThat(result.data().get(0).affinity()).isEqualTo(9.0);
    assertThat(result.data().get(1).avgRating()).isEqualTo(3.0);
    ArgumentCaptor<RestaurantFilter> filterCaptor = ArgumentCaptor.forClass(RestaurantFilter.class);
    verify(restaurants)
        .findAll(any(), eq(20), filterCaptor.capture(), eq(RestaurantSort.RATING_DESC));
    assertThat(filterCaptor.getValue().idsAllowList()).containsExactlyInAnyOrder(rid1, rid2);
  }

  @Test
  void distanceSort_passesAnchorIntoFilter() {
    RestaurantId rid = RestaurantId.newId();
    Coordinates anchor = new Coordinates(48.8566, 2.3522);
    Restaurant r =
        new Restaurant(
            rid,
            "Near",
            "addr",
            new Coordinates(48.86, 2.35),
            Instant.parse("2026-05-01T00:00:00Z"),
            null,
            120.0);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findAllRecommendedAffinities(requesterId, false))
        .thenReturn(Map.of(rid, new RestaurantAffinity(rid, 5.0, 1)));
    when(restaurants.findAll(any(), eq(20), any(), eq(RestaurantSort.DISTANCE_ASC)))
        .thenReturn(new CursorPage<>(List.of(r), false));

    CursorPage<Recommendation> result =
        service.list(
            SUBJECT,
            null,
            20,
            false,
            RecommendationSort.DISTANCE_ASC,
            anchor,
            RestaurantFilter.none());

    assertThat(result.data().getFirst().distanceMetres()).isEqualTo(120.0);
    ArgumentCaptor<RestaurantFilter> filterCaptor = ArgumentCaptor.forClass(RestaurantFilter.class);
    verify(restaurants)
        .findAll(any(), eq(20), filterCaptor.capture(), eq(RestaurantSort.DISTANCE_ASC));
    assertThat(filterCaptor.getValue().anchor()).isEqualTo(anchor);
  }

  @Test
  void nonAffinitySort_emptyAffinityMap_returnsEmptyPageWithoutPostgresHit() {
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findAllRecommendedAffinities(requesterId, false)).thenReturn(Map.of());

    CursorPage<Recommendation> result =
        service.list(
            SUBJECT, null, 20, false, RecommendationSort.NAME_ASC, null, RestaurantFilter.none());

    assertThat(result.data()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    verifyNoInteractions(restaurants);
  }

  @Test
  void nameSort_translatesCursorToRestaurantCursor() {
    RestaurantId rid = RestaurantId.newId();
    RecommendationCursor.ByName cursor =
        new RecommendationCursor.ByName("Foo", new RestaurantId(java.util.UUID.randomUUID()));
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findAllRecommendedAffinities(requesterId, false))
        .thenReturn(Map.of(rid, new RestaurantAffinity(rid, 5.0, 1)));
    when(restaurants.findAll(any(), eq(20), any(), eq(RestaurantSort.NAME_ASC)))
        .thenReturn(new CursorPage<>(List.of(), false));

    service.list(
        SUBJECT, cursor, 20, false, RecommendationSort.NAME_ASC, null, RestaurantFilter.none());

    ArgumentCaptor<RestaurantCursor> cursorCaptor = ArgumentCaptor.forClass(RestaurantCursor.class);
    verify(restaurants).findAll(cursorCaptor.capture(), eq(20), any(), eq(RestaurantSort.NAME_ASC));
    assertThat(cursorCaptor.getValue()).isInstanceOf(RestaurantCursor.ByName.class);
    RestaurantCursor.ByName translated = (RestaurantCursor.ByName) cursorCaptor.getValue();
    assertThat(translated.name()).isEqualTo("Foo");
    assertThat(translated.id()).isEqualTo(cursor.id().value());
  }

  @Test
  void ratingSort_propagatesCallerFilterDims() {
    RestaurantId rid = RestaurantId.newId();
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findAllRecommendedAffinities(requesterId, false))
        .thenReturn(Map.of(rid, new RestaurantAffinity(rid, 5.0, 1)));
    when(restaurants.findAll(any(), eq(20), any(), eq(RestaurantSort.RATING_DESC)))
        .thenReturn(new CursorPage<>(List.of(), false));
    when(expandTagSlugs.expand(any()))
        .thenAnswer(
            inv -> {
              java.util.Collection<String> slugs = inv.getArgument(0);
              java.util.Map<String, java.util.Set<String>> m = new java.util.LinkedHashMap<>();
              for (String s : slugs) {
                m.put(s, java.util.Set.of(s));
              }
              return m;
            });
    RestaurantFilter caller =
        new RestaurantFilter(
            List.of(List.of("vegan")), "bistrot", null, null, Boolean.TRUE, null, null);

    service.list(SUBJECT, null, 20, false, RecommendationSort.RATING_DESC, null, caller);

    ArgumentCaptor<RestaurantFilter> captor = ArgumentCaptor.forClass(RestaurantFilter.class);
    verify(restaurants).findAll(any(), eq(20), captor.capture(), eq(RestaurantSort.RATING_DESC));
    RestaurantFilter merged = captor.getValue();
    assertThat(merged.tagSlugGroups()).containsExactly(List.of("vegan"));
    assertThat(merged.name()).isEqualTo("bistrot");
    assertThat(merged.dineIn()).isTrue();
    assertThat(merged.idsAllowList()).containsExactly(rid);
  }

  @Test
  void affinitySort_withCallerFilter_paginatesInMemoryByAffinity() {
    RestaurantId rid1 = RestaurantId.newId();
    RestaurantId rid2 = RestaurantId.newId();
    RestaurantId rid3 = RestaurantId.newId();
    Restaurant r1 = restaurantWith(rid1, "A");
    Restaurant r2 = restaurantWith(rid2, "B");
    Restaurant r3 = restaurantWith(rid3, "C");
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findAllRecommendedAffinities(requesterId, false))
        .thenReturn(
            Map.of(
                rid1, new RestaurantAffinity(rid1, 9.0, 2),
                rid2, new RestaurantAffinity(rid2, 5.0, 1),
                rid3, new RestaurantAffinity(rid3, 7.0, 1)));
    when(restaurants.findAll(any(), eq(5000), any(), eq(RestaurantSort.CREATED_AT_DESC)))
        .thenReturn(new CursorPage<>(List.of(r1, r2, r3), false));
    when(expandTagSlugs.expand(any())).thenReturn(Map.of("vegan", java.util.Set.of("vegan")));
    RestaurantFilter caller =
        new RestaurantFilter(List.of(List.of("vegan")), null, null, null, null, null, null);

    CursorPage<Recommendation> page =
        service.list(SUBJECT, null, 2, false, RecommendationSort.AFFINITY_DESC, null, caller);

    assertThat(page.data()).hasSize(2);
    assertThat(page.data().get(0).affinity()).isEqualTo(9.0);
    assertThat(page.data().get(1).affinity()).isEqualTo(7.0);
    assertThat(page.hasNext()).isTrue();
    verify(graph, org.mockito.Mockito.never())
        .findRecommendations(any(), any(), anyInt(), anyBoolean());
  }

  @Test
  void affinitySort_withoutFilter_keepsCypherFastPath() {
    Recommendation reco =
        new Recommendation(RestaurantId.newId(), "Septime", "addr", 48.8, 2.3, 9.0, 2);
    CursorPage<Recommendation> expected = new CursorPage<>(List.of(reco), false);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(false))).thenReturn(expected);

    CursorPage<Recommendation> result =
        service.list(
            SUBJECT,
            null,
            20,
            false,
            RecommendationSort.AFFINITY_DESC,
            null,
            RestaurantFilter.none());

    assertThat(result).isSameAs(expected);
    verifyNoInteractions(restaurants);
  }

  private static Restaurant restaurantWith(RestaurantId id, String name) {
    return new Restaurant(
        id,
        name,
        "addr",
        new Coordinates(48.8, 2.3),
        Instant.parse("2026-05-01T00:00:00Z"),
        null,
        null);
  }
}
