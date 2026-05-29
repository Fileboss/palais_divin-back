package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationIdTest {

  @Test
  void rejectsNullUuid() {
    assertThatNullPointerException().isThrownBy(() -> new InvitationId(null)).withMessage("value");
  }

  @Test
  void newIdWrapsANonNullUuid() {
    InvitationId id = InvitationId.newId();

    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void newIdReturnsDistinctValues() {
    InvitationId a = InvitationId.newId();
    InvitationId b = InvitationId.newId();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void wrapsTheProvidedUuidUnchanged() {
    UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

    assertThat(new InvitationId(uuid).value()).isEqualTo(uuid);
  }
}
