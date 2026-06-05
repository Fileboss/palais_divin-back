package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Recommendation;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRecommendationsUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
      @AuthenticationPrincipal Jwt jwt) {
    RecommendationCursor decoded = cursor == null ? null : RecommendationCursorCodec.decode(cursor);
    CursorPage<Recommendation> page =
        getRecommendations.list(jwt.getSubject(), decoded, size, includeOwn);
    List<RecommendationResponse> data =
        page.data().stream().map(RecommendationResponse::from).toList();
    String nextCursor =
        page.hasNext() && !data.isEmpty()
            ? RecommendationCursorCodec.encode(
                new RecommendationCursor(
                    page.data().getLast().affinity(), page.data().getLast().restaurantId()))
            : null;
    return new RecommendationsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }
}
