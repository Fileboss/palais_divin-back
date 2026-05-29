package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

  @Test
  void rejectsNullUuid() {
    assertThatNullPointerException().isThrownBy(() -> new UserId(null)).withMessage("value");
  }

  @Test
  void newIdWrapsANonNullUuid() {
    UserId id = UserId.newId();

    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void newIdReturnsDistinctValues() {
    UserId a = UserId.newId();
    UserId b = UserId.newId();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void wrapsTheProvidedUuidUnchanged() {
    UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    assertThat(new UserId(uuid).value()).isEqualTo(uuid);
  }
}
