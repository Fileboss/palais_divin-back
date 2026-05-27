package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void encodeDecode_roundTrip() {
    RestaurantCursor original =
        new RestaurantCursor(
            Instant.parse("2026-05-27T10:15:30.123Z"),
            UUID.fromString("11111111-2222-3333-4444-555555555555"));

    String token = CursorCodec.encode(original);
    RestaurantCursor decoded = CursorCodec.decode(token);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void decode_invalidBase64_throws() {
    assertThatThrownBy(() -> CursorCodec.decode("not!base64!!"))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_notJson_throws() {
    assertThatThrownBy(() -> CursorCodec.decode(b64("not-json")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_missingField_throws() {
    assertThatThrownBy(
            () ->
                CursorCodec.decode(
                    b64("{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\"}")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongVersion_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":2}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableInstant_throws() {
    String json = "{\"k\":\"nope\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableUuid_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"nope\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongTypeField_throws() {
    String json = "{\"k\":42,\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }
}
