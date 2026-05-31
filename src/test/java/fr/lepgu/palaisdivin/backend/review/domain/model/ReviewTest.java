package fr.lepgu.palaisdivin.backend.review.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReviewTest {

  private static final ReviewId ID = ReviewId.newId();
  private static final RestaurantId RESTAURANT_ID = RestaurantId.newId();
  private static final UserId AUTHOR_ID = UserId.newId();
  private static final Instant NOW = Instant.parse("2026-05-31T10:00:00Z");

  @Test
  void buildsWithValidInputs() {
    Review r = new Review(ID, RESTAURANT_ID, AUTHOR_ID, 4, "Excellente option vegan.", NOW);

    assertThat(r.id()).isEqualTo(ID);
    assertThat(r.restaurantId()).isEqualTo(RESTAURANT_ID);
    assertThat(r.authorId()).isEqualTo(AUTHOR_ID);
    assertThat(r.rating()).isEqualTo(4);
    assertThat(r.comment()).isEqualTo("Excellente option vegan.");
    assertThat(r.createdAt()).isEqualTo(NOW);
  }

  @Test
  void allowsNullComment() {
    Review r = new Review(ID, RESTAURANT_ID, AUTHOR_ID, 4, null, NOW);

    assertThat(r.comment()).isNull();
  }

  @Test
  void acceptsRatingBoundaries() {
    Review low = new Review(ID, RESTAURANT_ID, AUTHOR_ID, 1, null, NOW);
    Review high = new Review(ID, RESTAURANT_ID, AUTHOR_ID, 5, null, NOW);

    assertThat(low.rating()).isEqualTo(1);
    assertThat(high.rating()).isEqualTo(5);
  }

  @Test
  void rejectsNullId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Review(null, RESTAURANT_ID, AUTHOR_ID, 4, null, NOW))
        .withMessage("id");
  }

  @Test
  void rejectsNullRestaurantId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Review(ID, null, AUTHOR_ID, 4, null, NOW))
        .withMessage("restaurantId");
  }

  @Test
  void rejectsNullAuthorId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Review(ID, RESTAURANT_ID, null, 4, null, NOW))
        .withMessage("authorId");
  }

  @Test
  void rejectsNullCreatedAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Review(ID, RESTAURANT_ID, AUTHOR_ID, 4, null, null))
        .withMessage("createdAt");
  }

  @Test
  void rejectsRatingBelowOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Review(ID, RESTAURANT_ID, AUTHOR_ID, 0, null, NOW))
        .withMessageContaining("rating");
  }

  @Test
  void rejectsRatingAboveFive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Review(ID, RESTAURANT_ID, AUTHOR_ID, 6, null, NOW))
        .withMessageContaining("rating");
  }

  @Test
  void rejectsBlankComment() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Review(ID, RESTAURANT_ID, AUTHOR_ID, 4, "", NOW))
        .withMessageContaining("blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Review(ID, RESTAURANT_ID, AUTHOR_ID, 4, "   ", NOW))
        .withMessageContaining("blank");
  }
}
