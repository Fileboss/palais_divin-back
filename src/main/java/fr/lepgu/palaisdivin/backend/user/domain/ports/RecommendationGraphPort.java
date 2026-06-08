package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Map;

public interface RecommendationGraphPort {

  CursorPage<Recommendation> findRecommendations(
      UserId requester, RecommendationCursor.ByAffinity cursor, int size, boolean includeOwn);

  Map<RestaurantId, RestaurantAffinity> findAllRecommendedAffinities(
      UserId requester, boolean includeOwn);

  RestaurantAffinity findAffinityFor(UserId requester, RestaurantId restaurant);
}
