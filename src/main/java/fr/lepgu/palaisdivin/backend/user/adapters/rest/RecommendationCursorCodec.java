package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

final class RecommendationCursorCodec {

  private static final int VERSION = 1;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private RecommendationCursorCodec() {}

  static String encode(RecommendationCursor cursor) {
    try {
      String json =
          MAPPER
              .createObjectNode()
              .put("a", cursor.affinity())
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

  static RecommendationCursor decode(String token) {
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
    JsonNode a = node.get("a");
    JsonNode id = node.get("id");
    JsonNode v = node.get("v");
    if (a == null || !a.isNumber() || id == null || !id.isTextual() || v == null || !v.isInt()) {
      throw new InvalidCursorException();
    }
    if (v.asInt() != VERSION) {
      throw new InvalidCursorException();
    }
    UUID uuid;
    try {
      uuid = UUID.fromString(id.asText());
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException();
    }
    return new RecommendationCursor(a.asDouble(), new RestaurantId(uuid));
  }
}
