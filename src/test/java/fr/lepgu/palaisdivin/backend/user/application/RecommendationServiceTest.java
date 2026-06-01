package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  private static final String SUBJECT = "kc-subject-abc";

  @Mock UserRepositoryPort users;
  @Mock RecommendationGraphPort graph;

  RecommendationService service;

  UserId requesterId;
  User requester;

  @BeforeEach
  void setUp() {
    service = new RecommendationService(users, graph);
    requesterId = UserId.newId();
    requester =
        new User(
            requesterId, SUBJECT, "me@example.com", "Me", Instant.parse("2026-05-01T00:00:00Z"));
  }

  @Test
  void list_resolvesSubjectAndDelegatesToGraph() {
    Recommendation reco =
        new Recommendation(
            RestaurantId.newId(), "Septime", "80 rue de Charonne", 48.8, 2.3, 9.0, 2);
    CursorPage<Recommendation> expected = new CursorPage<>(List.of(reco), false);

    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(requester));
    when(graph.findRecommendations(eq(requesterId), any(), eq(20))).thenReturn(expected);

    CursorPage<Recommendation> result = service.list(SUBJECT, null, 20);

    assertThat(result).isSameAs(expected);
    verify(graph).findRecommendations(requesterId, null, 20);
  }

  @Test
  void list_passesCursorThroughUnchanged() {
    RecommendationCursor cursor = new RecommendationCursor(7.5, RestaurantId.newId());
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(requester));
    when(graph.findRecommendations(requesterId, cursor, 5))
        .thenReturn(new CursorPage<>(List.of(), false));

    service.list(SUBJECT, cursor, 5);

    verify(graph).findRecommendations(requesterId, cursor, 5);
  }

  @Test
  void list_unknownSubject_throwsIllegalState() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list(SUBJECT, null, 20))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SUBJECT);

    verifyNoInteractions(graph);
  }
}
