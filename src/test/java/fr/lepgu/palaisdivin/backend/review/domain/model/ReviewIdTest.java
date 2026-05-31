package fr.lepgu.palaisdivin.backend.review.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewIdTest {

  @Test
  void rejectsNullUuid() {
    assertThatNullPointerException().isThrownBy(() -> new ReviewId(null)).withMessage("value");
  }

  @Test
  void newIdWrapsANonNullUuid() {
    ReviewId id = ReviewId.newId();

    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void newIdReturnsDistinctValues() {
    ReviewId a = ReviewId.newId();
    ReviewId b = ReviewId.newId();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void wrapsTheProvidedUuidUnchanged() {
    UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    assertThat(new ReviewId(uuid).value()).isEqualTo(uuid);
  }
}
