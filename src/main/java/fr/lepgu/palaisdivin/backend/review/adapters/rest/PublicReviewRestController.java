package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.ports.FindReviewByAuthorUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.LookupUsersUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
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
  private final FindReviewByAuthorUseCase findReviewByAuthor;
  private final LookupUsersUseCase lookupUsers;

  PublicReviewRestController(
      ListReviewsUseCase listReviews,
      FindReviewByAuthorUseCase findReviewByAuthor,
      LookupUsersUseCase lookupUsers) {
    this.listReviews = listReviews;
    this.findReviewByAuthor = findReviewByAuthor;
    this.lookupUsers = lookupUsers;
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

    List<UserId> authorIds = page.data().stream().map(Review::authorId).distinct().toList();
    Map<UserId, User> authors = lookupUsers.findByIds(authorIds);

    List<ReviewResponse> data =
        page.data().stream()
            .map(r -> ReviewResponse.from(r, displayNameOf(authors, r.authorId())))
            .toList();
    String nextCursor =
        page.hasNext() && !data.isEmpty()
            ? ReviewCursorCodec.encode(
                new ReviewCursor(page.data().getLast().createdAt(), page.data().getLast().id()))
            : null;
    return new ReviewsPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }

  @GetMapping("/author/{authorId}")
  ReviewResponse getByAuthor(@PathVariable UUID restaurantId, @PathVariable UUID authorId) {
    Review review =
        findReviewByAuthor.findByRestaurantAndAuthor(
            new RestaurantId(restaurantId), new UserId(authorId));
    Map<UserId, User> authors = lookupUsers.findByIds(List.of(review.authorId()));
    return ReviewResponse.from(review, displayNameOf(authors, review.authorId()));
  }

  private static String displayNameOf(Map<UserId, User> authors, UserId id) {
    User u = authors.get(id);
    return u == null ? null : u.displayName();
  }
}
