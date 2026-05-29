package fr.lepgu.palaisdivin.backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotUsableException.Reason;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvitationNotUsableExceptionTest {

  private static final Instant T = Instant.parse("2026-05-29T10:00:00Z");

  @Test
  void expiredCarriesExpiredReasonAndTimestampInMessage() {
    InvitationNotUsableException ex = InvitationNotUsableException.expired(T);

    assertThat(ex.reason()).isEqualTo(Reason.EXPIRED);
    assertThat(ex.getMessage()).contains(T.toString());
  }

  @Test
  void alreadyConsumedCarriesConsumedReasonAndTimestampInMessage() {
    InvitationNotUsableException ex = InvitationNotUsableException.alreadyConsumed(T);

    assertThat(ex.reason()).isEqualTo(Reason.ALREADY_CONSUMED);
    assertThat(ex.getMessage()).contains(T.toString());
  }

  @Test
  void expiredRejectsNullInstant() {
    assertThatNullPointerException()
        .isThrownBy(() -> InvitationNotUsableException.expired(null))
        .withMessage("expiresAt");
  }

  @Test
  void alreadyConsumedRejectsNullInstant() {
    assertThatNullPointerException()
        .isThrownBy(() -> InvitationNotUsableException.alreadyConsumed(null))
        .withMessage("consumedAt");
  }
}
