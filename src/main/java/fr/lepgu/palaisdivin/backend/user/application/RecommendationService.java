package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRecommendationsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecommendationService implements GetRecommendationsUseCase {

  private final UserRepositoryPort users;
  private final RecommendationGraphPort graph;

  public RecommendationService(UserRepositoryPort users, RecommendationGraphPort graph) {
    this.users = users;
    this.graph = graph;
  }

  @Override
  public CursorPage<Recommendation> list(
      String requesterSubject, RecommendationCursor cursor, int size) {
    UserId requester =
        users
            .findBySubject(requesterSubject)
            .map(User::id)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authenticated subject %s has no app_user row"
                            .formatted(requesterSubject)));
    return graph.findRecommendations(requester, cursor, size);
  }
}
