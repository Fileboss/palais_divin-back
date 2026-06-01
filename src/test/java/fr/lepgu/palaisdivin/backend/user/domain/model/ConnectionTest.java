package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConnectionTest {

  private static final ConnectionId ID = ConnectionId.newId();
  private static final UserId SOURCE = UserId.newId();
  private static final UserId TARGET = UserId.newId();
  private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

  @Test
  void buildsWithValidInputs() {
    Connection c = new Connection(ID, SOURCE, TARGET, NOW);

    assertThat(c.id()).isEqualTo(ID);
    assertThat(c.sourceUserId()).isEqualTo(SOURCE);
    assertThat(c.targetUserId()).isEqualTo(TARGET);
    assertThat(c.createdAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsNullId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Connection(null, SOURCE, TARGET, NOW))
        .withMessage("id");
  }

  @Test
  void rejectsNullSourceUserId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Connection(ID, null, TARGET, NOW))
        .withMessage("sourceUserId");
  }

  @Test
  void rejectsNullTargetUserId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Connection(ID, SOURCE, null, NOW))
        .withMessage("targetUserId");
  }

  @Test
  void rejectsNullCreatedAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Connection(ID, SOURCE, TARGET, null))
        .withMessage("createdAt");
  }

  @Test
  void rejectsSelfLoop() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Connection(ID, SOURCE, SOURCE, NOW))
        .withMessageContaining("self-loop");
  }
}
