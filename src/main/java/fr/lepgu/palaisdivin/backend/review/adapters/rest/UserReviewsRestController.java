package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.ports.GetMyReviewsBatchUseCase;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/user/reviews")
class UserReviewsRestController {

  private final GetMyReviewsBatchUseCase getMyReviewsBatch;

  UserReviewsRestController(GetMyReviewsBatchUseCase getMyReviewsBatch) {
    this.getMyReviewsBatch = getMyReviewsBatch;
  }

  @GetMapping
  MyReviewsBatchResponse listMine(
      @RequestParam("restaurantIds") @NotEmpty @Size(max = 100) List<UUID> restaurantIds,
      @AuthenticationPrincipal Jwt jwt) {
    List<RestaurantId> ids = restaurantIds.stream().map(RestaurantId::new).toList();
    Map<RestaurantId, Optional<Review>> batch =
        getMyReviewsBatch.getMyReviewsBatch(jwt.getSubject(), ids);

    Map<UUID, ReviewResponse> body = new LinkedHashMap<>(batch.size());
    batch.forEach(
        (id, maybe) -> body.put(id.value(), maybe.map(ReviewResponse::from).orElse(null)));
    return new MyReviewsBatchResponse(body);
  }
}
