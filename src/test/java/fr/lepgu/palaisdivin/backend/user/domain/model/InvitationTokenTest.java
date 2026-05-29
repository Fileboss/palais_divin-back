package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class InvitationTokenTest {

  @Test
  void rejectsNullValue() {
    assertThatNullPointerException()
        .isThrownBy(() -> new InvitationToken(null))
        .withMessage("value");
  }

  @Test
  void rejectsBlankValue() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvitationToken(""))
        .withMessageContaining("blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvitationToken("   "))
        .withMessageContaining("blank");
  }

  @Test
  void newTokenWrapsANonNullValue() {
    InvitationToken token = InvitationToken.newToken();

    assertThat(token).isNotNull();
    assertThat(token.value()).isNotBlank();
  }

  @Test
  void newTokenReturnsDistinctValues() {
    InvitationToken a = InvitationToken.newToken();
    InvitationToken b = InvitationToken.newToken();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void wrapsTheProvidedValueUnchanged() {
    InvitationToken token = new InvitationToken("opaque-token-abc");

    assertThat(token.value()).isEqualTo("opaque-token-abc");
  }
}
