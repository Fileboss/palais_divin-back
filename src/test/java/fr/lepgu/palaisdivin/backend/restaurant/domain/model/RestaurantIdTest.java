package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantIdTest {

  @Test
  void rejectsNullUuid() {
    assertThatNullPointerException().isThrownBy(() -> new RestaurantId(null)).withMessage("value");
  }

  @Test
  void newIdWrapsANonNullUuid() {
    RestaurantId id = RestaurantId.newId();

    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void newIdReturnsDistinctValues() {
    RestaurantId a = RestaurantId.newId();
    RestaurantId b = RestaurantId.newId();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void wrapsTheProvidedUuidUnchanged() {
    UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    assertThat(new RestaurantId(uuid).value()).isEqualTo(uuid);
  }
}
