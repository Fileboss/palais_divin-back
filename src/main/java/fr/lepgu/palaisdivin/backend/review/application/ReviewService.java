package fr.lepgu.palaisdivin.backend.review.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.review.domain.ReviewNotFoundException;
import fr.lepgu.palaisdivin.backend.review.domain.events.ReviewCreated;
import fr.lepgu.palaisdivin.backend.review.domain.events.ReviewUpdated;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.CreateReviewUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.FindReviewByAuthorUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ListReviewsUseCase;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ReviewRepositoryPort;
import fr.lepgu.palaisdivin.backend.review.domain.ports.UpdateReviewUseCase;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.IdempotencyKeyPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReviewService
    implements CreateReviewUseCase,
        FindReviewByAuthorUseCase,
        ListReviewsUseCase,
        UpdateReviewUseCase {

  private static final String AGGREGATE_TYPE = "Review";
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

  private final ReviewRepositoryPort reviews;
  private final UserRepositoryPort users;
  private final RestaurantRepositoryPort restaurants;
  private final IdempotencyKeyPort idempotency;
  private final OutboxPublisher outbox;
  private final Clock clock;

  public ReviewService(
      ReviewRepositoryPort reviews,
      UserRepositoryPort users,
      RestaurantRepositoryPort restaurants,
      IdempotencyKeyPort idempotency,
      OutboxPublisher outbox,
      Clock clock) {
    this.reviews = reviews;
    this.users = users;
    this.restaurants = restaurants;
    this.idempotency = idempotency;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public Review create(
      String authorSubject,
      RestaurantId restaurantId,
      int rating,
      String comment,
      Optional<String> idempotencyKey) {
    UserId authorId =
        users
            .findBySubject(authorSubject)
            .map(User::id)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authenticated subject %s has no app_user row".formatted(authorSubject)));

    if (idempotencyKey.isPresent()) {
      Optional<UUID> existingId =
          idempotency.findRecent(idempotencyKey.get(), authorId, AGGREGATE_TYPE, IDEMPOTENCY_TTL);
      if (existingId.isPresent()) {
        return reviews
            .findById(new ReviewId(existingId.get()))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Idempotency row points at missing review " + existingId.get()));
      }
    }

    if (restaurants.findById(restaurantId).isEmpty()) {
      throw new RestaurantNotFoundException(restaurantId);
    }

    Review review =
        new Review(ReviewId.newId(), restaurantId, authorId, rating, comment, clock.instant());
    Review saved = reviews.save(review);

    outbox.publish(
        AGGREGATE_TYPE,
        saved.id().value(),
        "ReviewCreated",
        new ReviewCreated(
            saved.id().value(),
            saved.restaurantId().value(),
            saved.authorId().value(),
            saved.rating(),
            saved.comment(),
            saved.createdAt()));

    idempotencyKey.ifPresent(
        key -> idempotency.record(key, authorId, AGGREGATE_TYPE, saved.id().value()));

    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  public CursorPage<Review> listByRestaurant(
      RestaurantId restaurantId, ReviewCursor cursor, int size) {
    return reviews.findByRestaurant(restaurantId, cursor, size);
  }

  @Override
  @Transactional(readOnly = true)
  public Review findByRestaurantAndAuthor(RestaurantId restaurantId, UserId authorId) {
    return reviews
        .findByRestaurantAndAuthor(restaurantId, authorId)
        .orElseThrow(() -> new ReviewNotFoundException(restaurantId, authorId));
  }

  @Override
  public Review update(
      String authorSubject, RestaurantId restaurantId, int rating, String comment) {
    UserId authorId =
        users
            .findBySubject(authorSubject)
            .map(User::id)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authenticated subject %s has no app_user row".formatted(authorSubject)));

    Review existing =
        reviews
            .findByRestaurantAndAuthor(restaurantId, authorId)
            .orElseThrow(() -> new ReviewNotFoundException(restaurantId, authorId));

    Review updated =
        new Review(
            existing.id(),
            existing.restaurantId(),
            existing.authorId(),
            rating,
            comment,
            existing.createdAt());
    Review saved = reviews.save(updated);

    outbox.publish(
        AGGREGATE_TYPE,
        saved.id().value(),
        "ReviewUpdated",
        new ReviewUpdated(
            saved.id().value(),
            saved.restaurantId().value(),
            saved.authorId().value(),
            saved.rating(),
            saved.comment(),
            saved.createdAt()));

    return saved;
  }
}
