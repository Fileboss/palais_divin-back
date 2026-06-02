package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantAffinityServiceTest {

  private static final String SUBJECT = "kc-subject-abc";

  @Mock UserRepositoryPort users;
  @Mock RestaurantRepositoryPort restaurants;
  @Mock RecommendationGraphPort graph;

  RestaurantAffinityService service;

  UserId requesterId;
  User requester;
  RestaurantId restaurantId;
  Restaurant restaurant;

  @BeforeEach
  void setUp() {
    service = new RestaurantAffinityService(users, restaurants, graph);
    requesterId = UserId.newId();
    requester =
        new User(
            requesterId, SUBJECT, "me@example.com", "Me", Instant.parse("2026-05-01T00:00:00Z"));
    restaurantId = RestaurantId.newId();
    restaurant =
        new Restaurant(
            restaurantId,
            "Septime",
            "80 rue de Charonne",
            new Coordinates(48.8, 2.3),
            Instant.parse("2026-05-01T00:00:00Z"),
            null);
  }

  @Test
  void getFor_resolvesSubjectAndDelegatesToGraph() {
    RestaurantAffinity expected = new RestaurantAffinity(restaurantId, 9.0, 2);
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(requester));
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(graph.findAffinityFor(requesterId, restaurantId)).thenReturn(expected);

    RestaurantAffinity result = service.getFor(SUBJECT, restaurantId);

    assertThat(result).isSameAs(expected);
    verify(graph).findAffinityFor(requesterId, restaurantId);
  }

  @Test
  void getFor_unknownSubject_throwsIllegalState() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getFor(SUBJECT, restaurantId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SUBJECT);

    verifyNoInteractions(restaurants, graph);
  }

  @Test
  void getFor_unknownRestaurant_throwsRestaurantNotFound() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(requester));
    when(restaurants.findById(restaurantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getFor(SUBJECT, restaurantId))
        .isInstanceOf(RestaurantNotFoundException.class);

    verifyNoInteractions(graph);
  }

  @Test
  void getFor_graphReturnsZeroAffinity_passesThrough() {
    RestaurantAffinity zero = new RestaurantAffinity(restaurantId, 0.0, 0);
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(requester));
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(graph.findAffinityFor(requesterId, restaurantId)).thenReturn(zero);

    RestaurantAffinity result = service.getFor(SUBJECT, restaurantId);

    assertThat(result).isSameAs(zero);
  }
}
