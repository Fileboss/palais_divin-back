package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewCursor;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class ReviewCursorCodec {

  private static final int VERSION = 1;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ReviewCursorCodec() {}

  static String encode(ReviewCursor cursor) {
    try {
      String json =
          MAPPER
              .createObjectNode()
              .put("k", cursor.createdAt().toString())
              .put("id", cursor.id().value().toString())
              .put("v", VERSION)
              .toString();
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IllegalStateException("failed to encode cursor", e);
    }
  }

  static ReviewCursor decode(String token) {
    byte[] raw;
    try {
      raw = Base64.getUrlDecoder().decode(token);
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException();
    }
    JsonNode node;
    try {
      node = MAPPER.readTree(new String(raw, StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new InvalidCursorException();
    }
    if (node == null || !node.isObject()) {
      throw new InvalidCursorException();
    }
    JsonNode k = node.get("k");
    JsonNode id = node.get("id");
    JsonNode v = node.get("v");
    if (k == null || !k.isTextual() || id == null || !id.isTextual() || v == null || !v.isInt()) {
      throw new InvalidCursorException();
    }
    if (v.asInt() != VERSION) {
      throw new InvalidCursorException();
    }
    Instant createdAt;
    try {
      createdAt = Instant.parse(k.asText());
    } catch (DateTimeParseException e) {
      throw new InvalidCursorException();
    }
    UUID uuid;
    try {
      uuid = UUID.fromString(id.asText());
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException();
    }
    return new ReviewCursor(createdAt, new ReviewId(uuid));
  }
}
