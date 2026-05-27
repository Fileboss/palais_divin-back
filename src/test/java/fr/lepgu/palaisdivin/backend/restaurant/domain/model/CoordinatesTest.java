package fr.lepgu.palaisdivin.backend.restaurant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class CoordinatesTest {

  @Test
  void acceptsLowerBoundaries() {
    Coordinates c = new Coordinates(-90.0, -180.0);

    assertThat(c.latitude()).isEqualTo(-90.0);
    assertThat(c.longitude()).isEqualTo(-180.0);
  }

  @Test
  void acceptsUpperBoundaries() {
    Coordinates c = new Coordinates(90.0, 180.0);

    assertThat(c.latitude()).isEqualTo(90.0);
    assertThat(c.longitude()).isEqualTo(180.0);
  }

  @Test
  void acceptsRealWorldCoordinate() {
    Coordinates paris = new Coordinates(48.8566, 2.3522);

    assertThat(paris.latitude()).isEqualTo(48.8566);
    assertThat(paris.longitude()).isEqualTo(2.3522);
  }

  @Test
  void rejectsLatitudeAbove90() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Coordinates(90.0001, 0.0))
        .withMessageContaining("latitude");
  }

  @Test
  void rejectsLatitudeBelowMinus90() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Coordinates(-90.0001, 0.0))
        .withMessageContaining("latitude");
  }

  @Test
  void rejectsLongitudeAbove180() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Coordinates(0.0, 180.0001))
        .withMessageContaining("longitude");
  }

  @Test
  void rejectsLongitudeBelowMinus180() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Coordinates(0.0, -180.0001))
        .withMessageContaining("longitude");
  }
}
