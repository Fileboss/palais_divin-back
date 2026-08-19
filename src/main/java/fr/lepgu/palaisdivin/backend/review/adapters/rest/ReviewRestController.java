package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.ports.CreateReviewUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.GetMyReviewUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.UpdateReviewUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.FindUserUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/user/restaurants/{restaurantId}/reviews")
class ReviewRestController {

  private final CreateReviewUseCase createReview;
  private final UpdateReviewUseCase updateReview;
  private final GetMyReviewUseCase getMyReviewUseCase;
  private final FindUserUseCase findUser;

  ReviewRestController(
      CreateReviewUseCase createReview,
      UpdateReviewUseCase updateReview,
      GetMyReviewUseCase getMyReviewUseCase,
      FindUserUseCase findUser) {
    this.createReview = createReview;
    this.updateReview = updateReview;
    this.getMyReviewUseCase = getMyReviewUseCase;
    this.findUser = findUser;
  }

  @GetMapping
  ResponseEntity<ReviewResponse> getMyReview(
      @PathVariable UUID restaurantId, @AuthenticationPrincipal Jwt jwt) {
    Review review =
        getMyReviewUseCase.getMyReview(jwt.getSubject(), new RestaurantId(restaurantId));
    return ResponseEntity.ok(
        ReviewResponse.from(review, findUser.findById(review.authorId()).displayName()));
  }

  @PostMapping
  ResponseEntity<ReviewResponse> create(
      @PathVariable UUID restaurantId,
      @Valid @RequestBody CreateReviewRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    Review created =
        createReview.create(
            jwt.getSubject(),
            new RestaurantId(restaurantId),
            req.rating(),
            req.comment(),
            Optional.ofNullable(idempotencyKey));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id().value())
            .toUri();
    return ResponseEntity.created(location)
        .body(ReviewResponse.from(created, findUser.findById(created.authorId()).displayName()));
  }

  @PutMapping
  ResponseEntity<ReviewResponse> update(
      @PathVariable UUID restaurantId,
      @Valid @RequestBody CreateReviewRequest req,
      @AuthenticationPrincipal Jwt jwt) {
    Review updated =
        updateReview.update(
            jwt.getSubject(), new RestaurantId(restaurantId), req.rating(), req.comment());
    return ResponseEntity.ok(
        ReviewResponse.from(updated, findUser.findById(updated.authorId()).displayName()));
  }
}
