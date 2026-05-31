package fr.lepgu.palaisdivin.backend.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ReviewRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.IdempotencyKeyPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-31T12:00:00Z");
  private static final String SUBJECT = "kc-subject-123";

  @Mock ReviewRepositoryPort reviews;
  @Mock UserRepositoryPort users;
  @Mock RestaurantRepositoryPort restaurants;
  @Mock IdempotencyKeyPort idempotency;

  ReviewService service;

  RestaurantId restaurantId;
  UserId authorId;
  User author;
  Restaurant restaurant;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new ReviewService(reviews, users, restaurants, idempotency, clock);

    restaurantId = RestaurantId.newId();
    authorId = UserId.newId();
    author = new User(authorId, SUBJECT, "u@example.com", "U", NOW.minusSeconds(60));
    restaurant =
        new Restaurant(
            restaurantId, "Septime", "addr", new Coordinates(48.8, 2.3), NOW.minusSeconds(60));
  }

  @Test
  void createPersistsReviewAndReturnsIt() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(author));
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(reviews.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

    Review result = service.create(SUBJECT, restaurantId, 4, "Great", Optional.empty());

    assertThat(result.restaurantId()).isEqualTo(restaurantId);
    assertThat(result.authorId()).isEqualTo(authorId);
    assertThat(result.rating()).isEqualTo(4);
    assertThat(result.comment()).isEqualTo("Great");
    assertThat(result.createdAt()).isEqualTo(NOW);
    verify(idempotency, never()).record(any(), any(), any(), any());
  }

  @Test
  void createWithIdempotencyKeyHitReplaysExistingReview() {
    ReviewId existingId = ReviewId.newId();
    Review existing =
        new Review(existingId, restaurantId, authorId, 5, "Original", NOW.minusSeconds(3600));
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(author));
    when(idempotency.findRecent("KEY-1", authorId, "Review", Duration.ofHours(24)))
        .thenReturn(Optional.of(existingId.value()));
    when(reviews.findById(existingId)).thenReturn(Optional.of(existing));

    Review result = service.create(SUBJECT, restaurantId, 2, "Different", Optional.of("KEY-1"));

    assertThat(result).isEqualTo(existing);
    verify(reviews, never()).save(any());
    verify(restaurants, never()).findById(any());
    verify(idempotency, never()).record(any(), any(), any(), any());
  }

  @Test
  void createWithIdempotencyKeyMissPersistsAndRecords() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(author));
    when(idempotency.findRecent("KEY-2", authorId, "Review", Duration.ofHours(24)))
        .thenReturn(Optional.empty());
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(reviews.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

    Review result = service.create(SUBJECT, restaurantId, 3, null, Optional.of("KEY-2"));

    ArgumentCaptor<java.util.UUID> aggId = ArgumentCaptor.forClass(java.util.UUID.class);
    verify(idempotency).record(eq("KEY-2"), eq(authorId), eq("Review"), aggId.capture());
    assertThat(aggId.getValue()).isEqualTo(result.id().value());
  }

  @Test
  void createThrowsWhenRestaurantMissing() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(author));
    when(restaurants.findById(restaurantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(SUBJECT, restaurantId, 4, null, Optional.empty()))
        .isInstanceOf(RestaurantNotFoundException.class);

    verify(reviews, never()).save(any());
  }

  @Test
  void createThrowsWhenSubjectHasNoAppUser() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(SUBJECT, restaurantId, 4, null, Optional.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SUBJECT);

    verify(reviews, never()).save(any());
    verify(restaurants, never()).findById(any());
  }
}
