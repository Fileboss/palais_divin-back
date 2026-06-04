package fr.lepgu.palaisdivin.backend.review.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, ReviewPostgresAdapter.class})
class ReviewPostgresAdapterIT {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-31T10:00:00Z");

  private static final UUID RESTAURANT_UUID = UUID.randomUUID();
  private static final UUID AUTHOR_UUID = UUID.randomUUID();
  private static final UUID OTHER_AUTHOR_UUID = UUID.randomUUID();

  private static final RestaurantId RESTAURANT_ID = new RestaurantId(RESTAURANT_UUID);
  private static final UserId AUTHOR_ID = new UserId(AUTHOR_UUID);
  private static final UserId OTHER_AUTHOR_ID = new UserId(OTHER_AUTHOR_UUID);

  @Autowired ReviewPostgresAdapter adapter;
  @PersistenceContext EntityManager em;

  @BeforeEach
  void seedFkTargets() {
    em.createNativeQuery(
            "INSERT INTO restaurant (id, name, location, created_at)"
                + " VALUES (?, ?, ST_GeographyFromText('SRID=4326;POINT(2.35 48.85)'), now())")
        .setParameter(1, RESTAURANT_UUID)
        .setParameter(2, "Le Train Bleu")
        .executeUpdate();
    insertUser(AUTHOR_UUID, "subj-a", "a@example.com", "Author A");
    insertUser(OTHER_AUTHOR_UUID, "subj-b", "b@example.com", "Author B");
  }

  private void insertUser(UUID id, String subject, String email, String displayName) {
    em.createNativeQuery(
            "INSERT INTO app_user (id, subject, email, display_name) VALUES (?, ?, ?, ?)")
        .setParameter(1, id)
        .setParameter(2, subject)
        .setParameter(3, email)
        .setParameter(4, displayName)
        .executeUpdate();
  }

  @Test
  void roundTripPreservesAllFields() {
    ReviewId id = ReviewId.newId();
    Review input =
        new Review(id, RESTAURANT_ID, AUTHOR_ID, 4, "Excellente option.", FIXED_CREATED_AT);

    Review saved = adapter.save(input);
    Optional<Review> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Review out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.restaurantId()).isEqualTo(RESTAURANT_ID);
    assertThat(out.authorId()).isEqualTo(AUTHOR_ID);
    assertThat(out.rating()).isEqualTo(4);
    assertThat(out.comment()).isEqualTo("Excellente option.");
    assertThat(out.createdAt()).isEqualTo(FIXED_CREATED_AT);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(adapter.findById(ReviewId.newId())).isEmpty();
  }

  @Test
  void nullCommentRoundTrips() {
    ReviewId id = ReviewId.newId();
    Review input = new Review(id, RESTAURANT_ID, AUTHOR_ID, 3, null, FIXED_CREATED_AT);

    adapter.save(input);

    assertThat(adapter.findById(id))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.comment()).isNull());
  }

  @Test
  void uniqueRestaurantAuthorPairIsEnforced() {
    Review first =
        new Review(ReviewId.newId(), RESTAURANT_ID, AUTHOR_ID, 4, "First", FIXED_CREATED_AT);
    Review dup =
        new Review(ReviewId.newId(), RESTAURANT_ID, AUTHOR_ID, 2, "Second", FIXED_CREATED_AT);
    adapter.save(first);

    assertThatThrownBy(
            () -> {
              adapter.save(dup);
              em.flush();
            })
        .hasMessageContaining("uq_review_restaurant_author");
  }

  @Test
  void differentAuthorsCanReviewSameRestaurant() {
    ReviewId a = ReviewId.newId();
    ReviewId b = ReviewId.newId();
    adapter.save(new Review(a, RESTAURANT_ID, AUTHOR_ID, 5, null, FIXED_CREATED_AT));
    adapter.save(new Review(b, RESTAURANT_ID, OTHER_AUTHOR_ID, 3, null, FIXED_CREATED_AT));

    assertThat(adapter.findById(a)).isPresent();
    assertThat(adapter.findById(b)).isPresent();
  }

  @Test
  void findByRestaurantWalksAllPagesByCursorDescendingByCreatedAtThenId() {
    UUID otherRestaurantUuid = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO restaurant (id, name, location, created_at)"
                + " VALUES (?, ?, ST_GeographyFromText('SRID=4326;POINT(2.35 48.85)'), now())")
        .setParameter(1, otherRestaurantUuid)
        .setParameter(2, "Other")
        .executeUpdate();
    RestaurantId otherRestaurantId = new RestaurantId(otherRestaurantUuid);

    List<ReviewId> targetIds = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      ReviewId id = ReviewId.newId();
      targetIds.add(id);
      UserId author = new UserId(UUID.randomUUID());
      insertUser(author.value(), "subj-t-" + i, "t" + i + "@example.com", "T" + i);
      adapter.save(
          new Review(
              id, RESTAURANT_ID, author, 4, "r-" + i, FIXED_CREATED_AT.plusSeconds(i * 10L)));
    }
    UserId otherAuthor = new UserId(UUID.randomUUID());
    insertUser(otherAuthor.value(), "subj-other", "other@example.com", "Other");
    adapter.save(
        new Review(
            ReviewId.newId(),
            otherRestaurantId,
            otherAuthor,
            4,
            "other",
            FIXED_CREATED_AT.plusSeconds(500)));

    List<ReviewId> collected = new ArrayList<>();
    ReviewCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Review> page = adapter.findByRestaurant(RESTAURANT_ID, cursor, 2);
      page.data().forEach(r -> collected.add(r.id()));
      pages++;
      if (!page.hasNext()) break;
      Review last = page.data().getLast();
      cursor = new ReviewCursor(last.createdAt(), last.id());
      if (pages > 10) throw new AssertionError("paging did not terminate");
    }

    assertThat(pages).isEqualTo(3);
    assertThat(collected).doesNotHaveDuplicates();
    assertThat(collected).containsExactlyInAnyOrderElementsOf(targetIds);
  }

  @Test
  void findByRestaurantFirstPageWithSizeLargerThanDataHasNextFalse() {
    adapter.save(new Review(ReviewId.newId(), RESTAURANT_ID, AUTHOR_ID, 4, null, FIXED_CREATED_AT));
    adapter.save(
        new Review(ReviewId.newId(), RESTAURANT_ID, OTHER_AUTHOR_ID, 5, null, FIXED_CREATED_AT));

    CursorPage<Review> page = adapter.findByRestaurant(RESTAURANT_ID, null, 100);

    assertThat(page.data()).hasSize(2);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void findByRestaurantUnknownRestaurantReturnsEmptyPage() {
    CursorPage<Review> page =
        adapter.findByRestaurant(new RestaurantId(UUID.randomUUID()), null, 20);

    assertThat(page.data()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void findByRestaurantAndAuthor_returnsExistingReview() {
    Review saved =
        adapter.save(
            new Review(ReviewId.newId(), RESTAURANT_ID, AUTHOR_ID, 4, "Nice", FIXED_CREATED_AT));

    Optional<Review> found = adapter.findByRestaurantAndAuthor(RESTAURANT_ID, AUTHOR_ID);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(saved.id());
    assertThat(found.get().rating()).isEqualTo(4);
  }

  @Test
  void findByRestaurantAndAuthor_missingReview_returnsEmpty() {
    assertThat(
            adapter.findByRestaurantAndAuthor(
                new RestaurantId(UUID.randomUUID()), new UserId(UUID.randomUUID())))
        .isEmpty();
  }

  @Test
  void findByAuthorWalksAllPagesByCursorDescendingByCreatedAtThenId() {
    UUID otherRestaurantUuid = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO restaurant (id, name, location, created_at)"
                + " VALUES (?, ?, ST_GeographyFromText('SRID=4326;POINT(2.35 48.85)'), now())")
        .setParameter(1, otherRestaurantUuid)
        .setParameter(2, "Other")
        .executeUpdate();
    RestaurantId otherRestaurantId = new RestaurantId(otherRestaurantUuid);

    List<ReviewId> targetIds = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      ReviewId id = ReviewId.newId();
      targetIds.add(id);
      UUID restaurantUuid = UUID.randomUUID();
      em.createNativeQuery(
              "INSERT INTO restaurant (id, name, location, created_at)"
                  + " VALUES (?, ?, ST_GeographyFromText('SRID=4326;POINT(2.35 48.85)'), now())")
          .setParameter(1, restaurantUuid)
          .setParameter(2, "R-" + i)
          .executeUpdate();
      adapter.save(
          new Review(
              id,
              new RestaurantId(restaurantUuid),
              AUTHOR_ID,
              4,
              "r-" + i,
              FIXED_CREATED_AT.plusSeconds(i * 10L)));
    }
    adapter.save(
        new Review(
            ReviewId.newId(),
            otherRestaurantId,
            OTHER_AUTHOR_ID,
            4,
            "other",
            FIXED_CREATED_AT.plusSeconds(500)));

    List<ReviewId> collected = new ArrayList<>();
    ReviewCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Review> page = adapter.findByAuthor(AUTHOR_ID, cursor, 2);
      page.data().forEach(r -> collected.add(r.id()));
      pages++;
      if (!page.hasNext()) break;
      Review last = page.data().getLast();
      cursor = new ReviewCursor(last.createdAt(), last.id());
      if (pages > 10) throw new AssertionError("paging did not terminate");
    }

    assertThat(pages).isEqualTo(3);
    assertThat(collected).doesNotHaveDuplicates();
    assertThat(collected).containsExactlyInAnyOrderElementsOf(targetIds);
  }

  @Test
  void findByAuthorUnknownAuthorReturnsEmptyPage() {
    CursorPage<Review> page = adapter.findByAuthor(new UserId(UUID.randomUUID()), null, 20);

    assertThat(page.data()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void avgRatingIsUpdatedByTriggerOnReviewSave() {
    adapter.save(new Review(ReviewId.newId(), RESTAURANT_ID, AUTHOR_ID, 4, null, FIXED_CREATED_AT));
    adapter.save(
        new Review(ReviewId.newId(), RESTAURANT_ID, OTHER_AUTHOR_ID, 2, null, FIXED_CREATED_AT));
    em.flush();
    em.clear();

    BigDecimal avgRating =
        (BigDecimal)
            em.createNativeQuery("SELECT avg_rating FROM restaurant WHERE id = ?")
                .setParameter(1, RESTAURANT_UUID)
                .getSingleResult();

    assertThat(avgRating).isNotNull();
    assertThat(avgRating.doubleValue()).isEqualTo(3.0);
  }
}
