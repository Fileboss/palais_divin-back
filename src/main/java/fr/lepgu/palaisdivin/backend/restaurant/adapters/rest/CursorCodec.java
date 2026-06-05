package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class CursorCodec {

  private static final int V_CREATED_AT = 1;
  private static final int V_RATING = 2;
  private static final int V_NAME = 3;
  private static final int V_DISTANCE = 4;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CursorCodec() {}

  static String encode(RestaurantCursor cursor) {
    try {
      ObjectNode node = MAPPER.createObjectNode();
      switch (cursor) {
        case RestaurantCursor.ByCreatedAt c -> {
          node.put("k", c.createdAt().toString());
          node.put("id", c.id().toString());
          node.put("v", V_CREATED_AT);
        }
        case RestaurantCursor.ByRating c -> {
          if (c.avgRating() == null) {
            node.putNull("r");
          } else {
            node.put("r", c.avgRating());
          }
          node.put("id", c.id().toString());
          node.put("v", V_RATING);
        }
        case RestaurantCursor.ByName c -> {
          node.put("n", c.name());
          node.put("id", c.id().toString());
          node.put("v", V_NAME);
        }
        case RestaurantCursor.ByDistance c -> {
          node.put("d", c.distanceMetres());
          node.put("id", c.id().toString());
          node.put("v", V_DISTANCE);
        }
      }
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IllegalStateException("failed to encode cursor", e);
    }
  }

  static RestaurantCursor decode(String token, RestaurantSort expectedSort) {
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
    JsonNode v = node.get("v");
    if (v == null || !v.isInt()) {
      throw new InvalidCursorException();
    }
    RestaurantCursor decoded =
        switch (v.asInt()) {
          case V_CREATED_AT -> decodeByCreatedAt(node);
          case V_RATING -> decodeByRating(node);
          case V_NAME -> decodeByName(node);
          case V_DISTANCE -> decodeByDistance(node);
          default -> throw new InvalidCursorException();
        };
    if (!matchesSort(decoded, expectedSort)) {
      throw new InvalidCursorException();
    }
    return decoded;
  }

  private static RestaurantCursor decodeByCreatedAt(JsonNode node) {
    JsonNode k = node.get("k");
    JsonNode id = node.get("id");
    if (k == null || !k.isTextual() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    Instant createdAt;
    try {
      createdAt = Instant.parse(k.asText());
    } catch (DateTimeParseException e) {
      throw new InvalidCursorException();
    }
    return new RestaurantCursor.ByCreatedAt(createdAt, parseUuid(id.asText()));
  }

  private static RestaurantCursor decodeByRating(JsonNode node) {
    JsonNode r = node.get("r");
    JsonNode id = node.get("id");
    if (r == null || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    BigDecimal avgRating;
    if (r.isNull()) {
      avgRating = null;
    } else if (r.isNumber()) {
      avgRating = r.decimalValue();
    } else {
      throw new InvalidCursorException();
    }
    return new RestaurantCursor.ByRating(avgRating, parseUuid(id.asText()));
  }

  private static RestaurantCursor decodeByName(JsonNode node) {
    JsonNode n = node.get("n");
    JsonNode id = node.get("id");
    if (n == null || !n.isTextual() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    return new RestaurantCursor.ByName(n.asText(), parseUuid(id.asText()));
  }

  private static RestaurantCursor decodeByDistance(JsonNode node) {
    JsonNode d = node.get("d");
    JsonNode id = node.get("id");
    if (d == null || !d.isNumber() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    return new RestaurantCursor.ByDistance(d.asDouble(), parseUuid(id.asText()));
  }

  private static UUID parseUuid(String s) {
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException();
    }
  }

  private static boolean matchesSort(RestaurantCursor cursor, RestaurantSort sort) {
    return switch (sort) {
      case CREATED_AT_DESC -> cursor instanceof RestaurantCursor.ByCreatedAt;
      case RATING_DESC -> cursor instanceof RestaurantCursor.ByRating;
      case NAME_ASC -> cursor instanceof RestaurantCursor.ByName;
      case DISTANCE_ASC -> cursor instanceof RestaurantCursor.ByDistance;
    };
  }
}
