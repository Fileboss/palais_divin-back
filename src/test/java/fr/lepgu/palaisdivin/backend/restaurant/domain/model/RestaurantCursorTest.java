package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantCursorTest {

  @Test
  void byCreatedAt_rejectsNullCreatedAt() {
    assertThatThrownBy(() -> new RestaurantCursor.ByCreatedAt(null, UUID.randomUUID()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void byCreatedAt_rejectsNullId() {
    assertThatThrownBy(() -> new RestaurantCursor.ByCreatedAt(Instant.now(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void byRating_allowsNullAvgRating() {
    UUID id = UUID.randomUUID();
    RestaurantCursor.ByRating cursor = new RestaurantCursor.ByRating(null, id);
    assertThat(cursor.avgRating()).isNull();
    assertThat(cursor.id()).isEqualTo(id);
  }

  @Test
  void byRating_rejectsNullId() {
    assertThatThrownBy(() -> new RestaurantCursor.ByRating(new BigDecimal("4.0"), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void byName_rejectsNullName() {
    assertThatThrownBy(() -> new RestaurantCursor.ByName(null, UUID.randomUUID()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void byName_rejectsNullId() {
    assertThatThrownBy(() -> new RestaurantCursor.ByName("Septime", null))
        .isInstanceOf(NullPointerException.class);
  }
}
