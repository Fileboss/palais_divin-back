package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RestaurantTest {

  private static final RestaurantId ID = RestaurantId.newId();
  private static final Coordinates LOCATION = new Coordinates(48.8566, 2.3522);
  private static final Instant NOW = Instant.parse("2026-05-27T10:00:00Z");

  @Test
  void buildsWithValidInputs() {
    Restaurant r = new Restaurant(ID, "Septime", "80 Rue de Charonne", LOCATION, NOW);

    assertThat(r.id()).isEqualTo(ID);
    assertThat(r.name()).isEqualTo("Septime");
    assertThat(r.address()).isEqualTo("80 Rue de Charonne");
    assertThat(r.location()).isEqualTo(LOCATION);
    assertThat(r.createdAt()).isEqualTo(NOW);
  }

  @Test
  void allowsNullAddress() {
    Restaurant r = new Restaurant(ID, "Septime", null, LOCATION, NOW);

    assertThat(r.address()).isNull();
  }

  @Test
  void rejectsNullId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Restaurant(null, "Septime", null, LOCATION, NOW))
        .withMessage("id");
  }

  @Test
  void rejectsNullName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Restaurant(ID, null, null, LOCATION, NOW))
        .withMessage("name");
  }

  @Test
  void rejectsBlankName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Restaurant(ID, "", null, LOCATION, NOW))
        .withMessageContaining("blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Restaurant(ID, "   ", null, LOCATION, NOW))
        .withMessageContaining("blank");
  }

  @Test
  void rejectsNullLocation() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Restaurant(ID, "Septime", null, null, NOW))
        .withMessage("location");
  }

  @Test
  void rejectsNullCreatedAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Restaurant(ID, "Septime", null, LOCATION, null))
        .withMessage("createdAt");
  }
}
