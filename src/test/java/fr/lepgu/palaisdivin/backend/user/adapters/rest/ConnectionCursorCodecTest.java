package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionCursorCodecTest {

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void encodeDecode_roundTrip() {
    ConnectionCursor original =
        new ConnectionCursor(
            Instant.parse("2026-06-01T10:15:30.123Z"),
            new ConnectionId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));

    String token = ConnectionCursorCodec.encode(original);
    ConnectionCursor decoded = ConnectionCursorCodec.decode(token);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void decode_invalidBase64_throws() {
    assertThatThrownBy(() -> ConnectionCursorCodec.decode("not!base64!!"))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_notJson_throws() {
    assertThatThrownBy(() -> ConnectionCursorCodec.decode(b64("not-json")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_missingField_throws() {
    assertThatThrownBy(
            () ->
                ConnectionCursorCodec.decode(
                    b64("{\"k\":\"2026-06-01T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\"}")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongVersion_throws() {
    String json = "{\"k\":\"2026-06-01T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":2}";
    assertThatThrownBy(() -> ConnectionCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableInstant_throws() {
    String json = "{\"k\":\"nope\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> ConnectionCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableUuid_throws() {
    String json = "{\"k\":\"2026-06-01T10:15:30Z\",\"id\":\"nope\",\"v\":1}";
    assertThatThrownBy(() -> ConnectionCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongTypeField_throws() {
    String json = "{\"k\":42,\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> ConnectionCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }
}
