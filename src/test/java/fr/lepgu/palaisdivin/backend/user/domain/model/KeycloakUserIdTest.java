package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class KeycloakUserIdTest {

  @Test
  void rejectsNullValue() {
    assertThatNullPointerException()
        .isThrownBy(() -> new KeycloakUserId(null))
        .withMessage("value");
  }

  @Test
  void rejectsBlankValue() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KeycloakUserId("   "))
        .withMessageContaining("must not be blank");
  }

  @Test
  void wrapsTheProvidedValueUnchanged() {
    String raw = "f2a4b6c8-1d3e-5f7a-9b0c-1d2e3f4a5b6c";

    assertThat(new KeycloakUserId(raw).value()).isEqualTo(raw);
  }
}
