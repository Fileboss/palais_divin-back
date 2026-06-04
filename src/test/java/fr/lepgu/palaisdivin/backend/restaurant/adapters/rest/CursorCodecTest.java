package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import java.math.BigDecimal;
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
  void encodeDecode_byCreatedAt_roundTrip() {
    RestaurantCursor original =
        new RestaurantCursor.ByCreatedAt(
            Instant.parse("2026-05-27T10:15:30.123Z"),
            UUID.fromString("11111111-2222-3333-4444-555555555555"));

    String token = CursorCodec.encode(original);
    RestaurantCursor decoded = CursorCodec.decode(token, RestaurantSort.CREATED_AT_DESC);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void encodeDecode_byRating_withRating_roundTrip() {
    RestaurantCursor original =
        new RestaurantCursor.ByRating(new BigDecimal("4.25"), UUID.randomUUID());

    String token = CursorCodec.encode(original);
    RestaurantCursor decoded = CursorCodec.decode(token, RestaurantSort.RATING_DESC);

    assertThat(decoded).isInstanceOf(RestaurantCursor.ByRating.class);
    RestaurantCursor.ByRating cast = (RestaurantCursor.ByRating) decoded;
    assertThat(cast.id()).isEqualTo(((RestaurantCursor.ByRating) original).id());
    assertThat(cast.avgRating()).isEqualByComparingTo(new BigDecimal("4.25"));
  }

  @Test
  void encodeDecode_byRating_withNullRating_roundTrip() {
    RestaurantCursor original = new RestaurantCursor.ByRating(null, UUID.randomUUID());

    String token = CursorCodec.encode(original);
    RestaurantCursor decoded = CursorCodec.decode(token, RestaurantSort.RATING_DESC);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void encodeDecode_byName_roundTrip() {
    RestaurantCursor original = new RestaurantCursor.ByName("Le Bistrot", UUID.randomUUID());

    String token = CursorCodec.encode(original);
    RestaurantCursor decoded = CursorCodec.decode(token, RestaurantSort.NAME_ASC);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void decode_v1Cursor_withRatingSort_throws() {
    String token =
        CursorCodec.encode(
            new RestaurantCursor.ByCreatedAt(
                Instant.parse("2026-05-27T10:15:30Z"), UUID.randomUUID()));

    assertThatThrownBy(() -> CursorCodec.decode(token, RestaurantSort.RATING_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v2Cursor_withCreatedAtSort_throws() {
    String token =
        CursorCodec.encode(new RestaurantCursor.ByRating(new BigDecimal("3.0"), UUID.randomUUID()));

    assertThatThrownBy(() -> CursorCodec.decode(token, RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v3Cursor_withRatingSort_throws() {
    String token = CursorCodec.encode(new RestaurantCursor.ByName("Septime", UUID.randomUUID()));

    assertThatThrownBy(() -> CursorCodec.decode(token, RestaurantSort.RATING_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_invalidBase64_throws() {
    assertThatThrownBy(() -> CursorCodec.decode("not!base64!!", RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_notJson_throws() {
    assertThatThrownBy(() -> CursorCodec.decode(b64("not-json"), RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_missingVersion_throws() {
    assertThatThrownBy(
            () ->
                CursorCodec.decode(
                    b64("{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\"}"),
                    RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_unknownVersion_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":99}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json), RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v1MissingField_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json), RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v1UnparseableInstant_throws() {
    String json = "{\"k\":\"nope\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json), RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v1UnparseableUuid_throws() {
    String json = "{\"k\":\"2026-05-27T10:15:30Z\",\"id\":\"nope\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json), RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v1WrongTypeField_throws() {
    String json = "{\"k\":42,\"id\":\"" + UUID.randomUUID() + "\",\"v\":1}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json), RestaurantSort.CREATED_AT_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void decode_v2RatingAsString_throws() {
    String json = "{\"r\":\"not-a-number\",\"id\":\"" + UUID.randomUUID() + "\",\"v\":2}";
    assertThatThrownBy(() -> CursorCodec.decode(b64(json), RestaurantSort.RATING_DESC))
        .isInstanceOf(InvalidCursorException.class);
  }
}
