package fr.lepgu.palaisdivin.backend.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAffinityRankedRestaurantsServiceTest {

  private static final String SUBJECT = "kc-subject-aff";

  @Mock UserRepositoryPort users;
  @Mock RecommendationGraphPort graph;
  @Mock RestaurantRepositoryPort restaurants;

  ListAffinityRankedRestaurantsService service;
  UserId requesterId;

  @BeforeEach
  void setUp() {
    service = new ListAffinityRankedRestaurantsService(users, graph, restaurants);
    requesterId = UserId.newId();
  }

  @Test
  void listIncludesOwnAndDelegatesToGraph() {
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(true)))
        .thenReturn(new CursorPage<>(List.of(), false));

    service.list(SUBJECT, null, 20);

    verify(graph).findRecommendations(requesterId, null, 20, true);
  }

  @Test
  void mergesAffinityIntoRestaurantPayload() {
    RestaurantId rid = RestaurantId.newId();
    Recommendation reco = new Recommendation(rid, "Septime", "addr", 48.8, 2.3, 9.5, 2);
    Restaurant restaurant =
        new Restaurant(
            rid,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.85, 2.38),
            Instant.parse("2026-05-01T00:00:00Z"),
            4.5);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(true)))
        .thenReturn(new CursorPage<>(List.of(reco), false));
    when(restaurants.findByIds(anyCollection())).thenReturn(Map.of(rid, restaurant));

    CursorPage<Restaurant> page = service.list(SUBJECT, null, 20);

    assertThat(page.data()).hasSize(1);
    assertThat(page.data().getFirst().affinity()).isEqualTo(9.5);
    assertThat(page.data().getFirst().avgRating()).isEqualTo(4.5);
    assertThat(page.data().getFirst().address()).isEqualTo("80 Rue de Charonne");
  }

  @Test
  void preservesGraphOrdering() {
    RestaurantId r1 = RestaurantId.newId();
    RestaurantId r2 = RestaurantId.newId();
    Recommendation hi = new Recommendation(r1, "Hi", "a", 0, 0, 9.0, 2);
    Recommendation lo = new Recommendation(r2, "Lo", "b", 0, 0, 5.0, 1);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(true)))
        .thenReturn(new CursorPage<>(List.of(hi, lo), false));
    when(restaurants.findByIds(anyCollection()))
        .thenReturn(
            Map.of(
                r1, restaurantOf(r1, "Hi"),
                r2, restaurantOf(r2, "Lo")));

    CursorPage<Restaurant> page = service.list(SUBJECT, null, 20);

    assertThat(page.data()).extracting(Restaurant::id).containsExactly(r1, r2);
  }

  @Test
  void skipsRestaurantsMissingInPostgres() {
    RestaurantId stale = RestaurantId.newId();
    RestaurantId live = RestaurantId.newId();
    Recommendation staleReco = new Recommendation(stale, "Gone", "a", 0, 0, 9.0, 2);
    Recommendation liveReco = new Recommendation(live, "Here", "b", 0, 0, 5.0, 1);
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(true)))
        .thenReturn(new CursorPage<>(List.of(staleReco, liveReco), false));
    when(restaurants.findByIds(anyCollection()))
        .thenReturn(Map.of(live, restaurantOf(live, "Here")));

    CursorPage<Restaurant> page = service.list(SUBJECT, null, 20);

    assertThat(page.data()).hasSize(1);
    assertThat(page.data().getFirst().id()).isEqualTo(live);
  }

  @Test
  void emptyGraphResult_doesNotHitPostgres() {
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(20), eq(true)))
        .thenReturn(new CursorPage<>(List.of(), false));

    CursorPage<Restaurant> page = service.list(SUBJECT, null, 20);

    assertThat(page.data()).isEmpty();
    verifyNoInteractions(restaurants);
  }

  @Test
  void translatesCursorIntoRecommendationCursor() {
    RestaurantId rid = RestaurantId.newId();
    RestaurantCursor.ByAffinity inbound =
        new RestaurantCursor.ByAffinity(7.5, java.util.UUID.randomUUID());
    when(users.requireBySubject(SUBJECT)).thenReturn(requesterId);
    when(graph.findRecommendations(eq(requesterId), any(), eq(10), eq(true)))
        .thenReturn(new CursorPage<>(List.of(), false));

    service.list(SUBJECT, inbound, 10);

    org.mockito.ArgumentCaptor<RecommendationCursor.ByAffinity> captor =
        org.mockito.ArgumentCaptor.forClass(RecommendationCursor.ByAffinity.class);
    verify(graph).findRecommendations(eq(requesterId), captor.capture(), eq(10), eq(true));
    assertThat(captor.getValue().affinity()).isEqualTo(7.5);
    assertThat(captor.getValue().id().value()).isEqualTo(inbound.id());
  }

  private static Restaurant restaurantOf(RestaurantId id, String name) {
    return new Restaurant(
        id, name, "addr", new Coordinates(48.0, 2.0), Instant.parse("2026-05-01T00:00:00Z"), null);
  }
}
