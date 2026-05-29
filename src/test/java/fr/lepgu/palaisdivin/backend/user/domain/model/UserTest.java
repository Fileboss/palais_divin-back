package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final UserId ID = UserId.newId();
  private static final String SUBJECT = "kc-sub-abc123";
  private static final String EMAIL = "alice@example.com";
  private static final String DISPLAY_NAME = "Alice";
  private static final Instant NOW = Instant.parse("2026-05-29T10:00:00Z");

  @Test
  void buildsWithValidInputs() {
    User u = new User(ID, SUBJECT, EMAIL, DISPLAY_NAME, NOW);

    assertThat(u.id()).isEqualTo(ID);
    assertThat(u.subject()).isEqualTo(SUBJECT);
    assertThat(u.email()).isEqualTo(EMAIL);
    assertThat(u.displayName()).isEqualTo(DISPLAY_NAME);
    assertThat(u.createdAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsNullId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new User(null, SUBJECT, EMAIL, DISPLAY_NAME, NOW))
        .withMessage("id");
  }

  @Test
  void rejectsNullSubject() {
    assertThatNullPointerException()
        .isThrownBy(() -> new User(ID, null, EMAIL, DISPLAY_NAME, NOW))
        .withMessage("subject");
  }

  @Test
  void rejectsBlankSubject() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User(ID, "", EMAIL, DISPLAY_NAME, NOW))
        .withMessageContaining("blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User(ID, "   ", EMAIL, DISPLAY_NAME, NOW))
        .withMessageContaining("blank");
  }

  @Test
  void rejectsNullEmail() {
    assertThatNullPointerException()
        .isThrownBy(() -> new User(ID, SUBJECT, null, DISPLAY_NAME, NOW))
        .withMessage("email");
  }

  @Test
  void rejectsBlankEmail() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User(ID, SUBJECT, "", DISPLAY_NAME, NOW))
        .withMessageContaining("blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User(ID, SUBJECT, "   ", DISPLAY_NAME, NOW))
        .withMessageContaining("blank");
  }

  @Test
  void rejectsNullDisplayName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new User(ID, SUBJECT, EMAIL, null, NOW))
        .withMessage("displayName");
  }

  @Test
  void rejectsBlankDisplayName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User(ID, SUBJECT, EMAIL, "", NOW))
        .withMessageContaining("blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new User(ID, SUBJECT, EMAIL, "   ", NOW))
        .withMessageContaining("blank");
  }

  @Test
  void rejectsNullCreatedAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new User(ID, SUBJECT, EMAIL, DISPLAY_NAME, null))
        .withMessage("createdAt");
  }
}
