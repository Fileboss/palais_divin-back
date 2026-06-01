package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;

public interface GetRecommendationsUseCase {

  CursorPage<Recommendation> list(String requesterSubject, RecommendationCursor cursor, int size);
}
