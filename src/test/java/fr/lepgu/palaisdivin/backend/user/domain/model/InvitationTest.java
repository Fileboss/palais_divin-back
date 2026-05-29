package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvitationTest {

  private static final InvitationId ID = InvitationId.newId();
  private static final InvitationToken TOKEN = InvitationToken.newToken();
  private static final Instant CREATED_AT = Instant.parse("2026-05-29T10:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(48L * 3600L);

  @Test
  void buildsWithValidInputs() {
    Invitation i = new Invitation(ID, TOKEN, EXPIRES_AT, null, CREATED_AT);

    assertThat(i.id()).isEqualTo(ID);
    assertThat(i.token()).isEqualTo(TOKEN);
    assertThat(i.expiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(i.consumedAt()).isNull();
    assertThat(i.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void acceptsNonNullConsumedAt() {
    Instant consumedAt = CREATED_AT.plusSeconds(60);

    Invitation i = new Invitation(ID, TOKEN, EXPIRES_AT, consumedAt, CREATED_AT);

    assertThat(i.consumedAt()).isEqualTo(consumedAt);
  }

  @Test
  void rejectsNullId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Invitation(null, TOKEN, EXPIRES_AT, null, CREATED_AT))
        .withMessage("id");
  }

  @Test
  void rejectsNullToken() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Invitation(ID, null, EXPIRES_AT, null, CREATED_AT))
        .withMessage("token");
  }

  @Test
  void rejectsNullExpiresAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Invitation(ID, TOKEN, null, null, CREATED_AT))
        .withMessage("expiresAt");
  }

  @Test
  void rejectsNullCreatedAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Invitation(ID, TOKEN, EXPIRES_AT, null, null))
        .withMessage("createdAt");
  }

  @Test
  void rejectsExpiresAtEqualToCreatedAt() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Invitation(ID, TOKEN, CREATED_AT, null, CREATED_AT))
        .withMessageContaining("must be after");
  }

  @Test
  void rejectsExpiresAtBeforeCreatedAt() {
    Instant before = CREATED_AT.minusSeconds(1);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Invitation(ID, TOKEN, before, null, CREATED_AT))
        .withMessageContaining("must be after");
  }
}
