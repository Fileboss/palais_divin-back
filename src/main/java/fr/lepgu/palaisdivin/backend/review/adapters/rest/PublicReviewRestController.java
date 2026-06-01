package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/restaurants/{restaurantId}/reviews")
class PublicReviewRestController {

  private final ListReviewsUseCase listReviews;

  PublicReviewRestController(ListReviewsUseCase listReviews) {
    this.listReviews = listReviews;
  }

  @GetMapping
  ReviewsPageResponse list(
      @PathVariable UUID restaurantId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "CREATED_AT_DESC") ReviewSort sort) {
    ReviewCursor decoded = cursor == null ? null : ReviewCursorCodec.decode(cursor);
    CursorPage<Review> page =
        listReviews.listByRestaurant(new RestaurantId(restaurantId), decoded, size);
    List<ReviewResponse> data = page.data().stream().map(ReviewResponse::from).toList();
    String nextCursor =
        page.hasNext() && !data.isEmpty()
            ? ReviewCursorCodec.encode(
                new ReviewCursor(page.data().getLast().createdAt(), page.data().getLast().id()))
            : null;
    return new ReviewsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }
}
