package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationCursorCodecTest {

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void encodeDecode_roundTrip() {
    RecommendationCursor original =
        new RecommendationCursor(
            12.5, new RestaurantId(UUID.fromString("11111111-2222-3333-4444-555555555555")));

    String token = RecommendationCursorCodec.encode(original);
    RecommendationCursor decoded = RecommendationCursorCodec.decode(token);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void decode_invalidBase64_throws() {
    assertThatThrownBy(() -> RecommendationCursorCodec.decode("not!base64!!"))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_notJson_throws() {
    assertThatThrownBy(() -> RecommendationCursorCodec.decode(b64("not-json")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_missingField_throws() {
    assertThatThrownBy(
            () ->
                RecommendationCursorCodec.decode(
                    b64("{\"a\":4.0,\"id\":\"" + UUID.randomUUID() + "\"}")))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_wrongVersion_throws() {
    String json = "{\"a\":4.0,\"id\":\"" + UUID.randomUUID() + "\",\"v\":2}";
    assertThatThrownBy(() -> RecommendationCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_affinityNotNumeric_throws() {
    String json = "{\"a\":\"not-a-number\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> RecommendationCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unparseableUuid_throws() {
    String json = "{\"a\":4.0,\"id\":\"nope\",\"v\":1}";
    assertThatThrownBy(() -> RecommendationCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_idNotTextual_throws() {
    String json = "{\"a\":4.0,\"id\":42,\"v\":1}";
    assertThatThrownBy(() -> RecommendationCursorCodec.decode(b64(json)))
        .isInstanceOf(InvalidCursorException.class);
  }
}
