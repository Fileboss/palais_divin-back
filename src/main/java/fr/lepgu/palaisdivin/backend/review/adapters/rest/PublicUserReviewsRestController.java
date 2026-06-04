package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/users/{userId}/reviews")
class PublicUserReviewsRestController {

  private final ListReviewsUseCase listReviews;
  private final RestaurantRepositoryPort restaurants;

  PublicUserReviewsRestController(
      ListReviewsUseCase listReviews, RestaurantRepositoryPort restaurants) {
    this.listReviews = listReviews;
    this.restaurants = restaurants;
  }

  @GetMapping
  AuthorReviewsPageResponse list(
      @PathVariable UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    ReviewCursor decoded = cursor == null ? null : ReviewCursorCodec.decode(cursor);
    CursorPage<Review> page = listReviews.listByAuthor(new UserId(userId), decoded, size);

    List<RestaurantId> restaurantIds =
        page.data().stream().map(Review::restaurantId).distinct().toList();
    Map<RestaurantId, Restaurant> restaurantsById = restaurants.findByIds(restaurantIds);

    List<AuthorReviewResponse> data =
        page.data().stream()
            .map(r -> mapItem(r, restaurantsById.get(r.restaurantId())))
            .filter(Objects::nonNull)
            .toList();
    String nextCursor =
        page.hasNext() && !page.data().isEmpty()
            ? ReviewCursorCodec.encode(
                new ReviewCursor(page.data().getLast().createdAt(), page.data().getLast().id()))
            : null;
    return new AuthorReviewsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }

  private static AuthorReviewResponse mapItem(Review r, Restaurant rest) {
    return rest == null ? null : AuthorReviewResponse.from(r, rest);
  }
}
