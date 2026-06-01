package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewCursorCodecTest {

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void encodeDecode_roundTrip() {
    ReviewCursor original =
        new ReviewCursor(
            Instant.parse("2026-05-27T10:15:30.123Z"),
            new ReviewId(UUID.fromString("11111111-2222-3333-4444-555555555555")));

    String token = ReviewCursorCodec.encode(original);
    ReviewCursor decoded = ReviewCursorCodec.decode(token);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void decode_invalidBase64_throws() {
    assertThatThrownBy(() -> ReviewCursorCodec.decode("not!base64!!"))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_notJson_throws() {
    assertThatThrownBy(() -> ReviewCursorCodec.decode(b64("not-json")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_missingField_throws() {
    assertThatThrownBy(
            () ->
                ReviewCursorCodec.decode(
                    b64("{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\"}")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongVersion_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":2}";
    assertThatThrownBy(() -> ReviewCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableInstant_throws() {
    String json = "{\"k\":\"nope\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> ReviewCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableUuid_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"nope\",\"v\":1}";
    assertThatThrownBy(() -> ReviewCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongTypeField_throws() {
    String json = "{\"k\":42,\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> ReviewCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }
}
