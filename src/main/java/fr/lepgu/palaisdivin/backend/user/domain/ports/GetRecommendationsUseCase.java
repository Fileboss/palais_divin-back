package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort;

public interface GetRecommendationsUseCase {

  CursorPage<Recommendation> list(
      String requesterSubject,
      RecommendationCursor cursor,
      int size,
      boolean includeOwn,
      RecommendationSort sort,
      Coordinates anchor);
}
