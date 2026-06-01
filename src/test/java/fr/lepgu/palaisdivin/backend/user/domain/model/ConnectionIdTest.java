package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionIdTest {

  @Test
  void rejectsNullUuid() {
    assertThatNullPointerException().isThrownBy(() -> new ConnectionId(null)).withMessage("value");
  }

  @Test
  void newIdWrapsANonNullUuid() {
    ConnectionId id = ConnectionId.newId();

    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void newIdReturnsDistinctValues() {
    ConnectionId a = ConnectionId.newId();
    ConnectionId b = ConnectionId.newId();

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void wrapsTheProvidedUuidUnchanged() {
    UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    assertThat(new ConnectionId(uuid).value()).isEqualTo(uuid);
  }
}
