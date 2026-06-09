package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.adapters.rest.MissingAnchorException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRecommendationsUseCase;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/user/recommendations")
class RecommendationRestController {

  private final GetRecommendationsUseCase getRecommendations;

  RecommendationRestController(GetRecommendationsUseCase getRecommendations) {
    this.getRecommendations = getRecommendations;
  }

  @GetMapping
  RecommendationsPageResponse list(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "AFFINITY_DESC") RecommendationSort sort,
      @RequestParam(defaultValue = "false") boolean includeOwn,
      @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,
      @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lng,
      @AuthenticationPrincipal Jwt jwt) {
    Coordinates anchor = (lat != null && lng != null) ? new Coordinates(lat, lng) : null;
    if (sort == RecommendationSort.DISTANCE_ASC && anchor == null) {
      throw new MissingAnchorException();
    }
    RecommendationCursor decoded =
        cursor == null ? null : RecommendationCursorCodec.decode(cursor, sort);
    CursorPage<Recommendation> page =
        getRecommendations.list(jwt.getSubject(), decoded, size, includeOwn, sort, anchor);
    List<RecommendationResponse> data =
        page.data().stream().map(RecommendationResponse::from).toList();
    String nextCursor =
        page.hasNext() && !page.data().isEmpty()
            ? RecommendationCursorCodec.encode(nextCursorFor(page.data().getLast(), sort))
            : null;
    return new RecommendationsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }

  private static RecommendationCursor nextCursorFor(Recommendation last, RecommendationSort sort) {
    return switch (sort) {
      case AFFINITY_DESC ->
          new RecommendationCursor.ByAffinity(last.affinity(), last.restaurantId());
      case RATING_DESC ->
          new RecommendationCursor.ByRating(
              last.avgRating() == null ? null : BigDecimal.valueOf(last.avgRating()),
              last.restaurantId());
      case NAME_ASC -> new RecommendationCursor.ByName(last.name(), last.restaurantId());
      case DISTANCE_ASC ->
          new RecommendationCursor.ByDistance(
              last.distanceMetres() == null ? 0.0 : last.distanceMetres(), last.restaurantId());
      case CREATED_AT_DESC ->
          new RecommendationCursor.ByCreatedAt(last.createdAt(), last.restaurantId());
    };
  }
}
