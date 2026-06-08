package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.RecommendationSort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class RecommendationCursorCodec {

  private static final int V_AFFINITY = 1;
  private static final int V_RATING = 2;
  private static final int V_NAME = 3;
  private static final int V_DISTANCE = 4;
  private static final int V_CREATED_AT = 5;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private RecommendationCursorCodec() {}

  static String encode(RecommendationCursor cursor) {
    try {
      ObjectNode node = MAPPER.createObjectNode();
      switch (cursor) {
        case RecommendationCursor.ByAffinity c -> {
          node.put("a", c.affinity());
          node.put("id", c.id().value().toString());
          node.put("v", V_AFFINITY);
        }
        case RecommendationCursor.ByRating c -> {
          if (c.avgRating() == null) {
            node.putNull("r");
          } else {
            node.put("r", c.avgRating());
          }
          node.put("id", c.id().value().toString());
          node.put("v", V_RATING);
        }
        case RecommendationCursor.ByName c -> {
          node.put("n", c.name());
          node.put("id", c.id().value().toString());
          node.put("v", V_NAME);
        }
        case RecommendationCursor.ByDistance c -> {
          node.put("d", c.distanceMetres());
          node.put("id", c.id().value().toString());
          node.put("v", V_DISTANCE);
        }
        case RecommendationCursor.ByCreatedAt c -> {
          node.put("k", c.createdAt().toString());
          node.put("id", c.id().value().toString());
          node.put("v", V_CREATED_AT);
        }
      }
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IllegalStateException("failed to encode cursor", e);
    }
  }

  static RecommendationCursor decode(String token, RecommendationSort expectedSort) {
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
    RecommendationCursor decoded =
        switch (v.asInt()) {
          case V_AFFINITY -> decodeByAffinity(node);
          case V_RATING -> decodeByRating(node);
          case V_NAME -> decodeByName(node);
          case V_DISTANCE -> decodeByDistance(node);
          case V_CREATED_AT -> decodeByCreatedAt(node);
          default -> throw new InvalidCursorException();
        };
    if (!matchesSort(decoded, expectedSort)) {
      throw new InvalidCursorException();
    }
    return decoded;
  }

  private static RecommendationCursor decodeByAffinity(JsonNode node) {
    JsonNode a = node.get("a");
    JsonNode id = node.get("id");
    if (a == null || !a.isNumber() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    return new RecommendationCursor.ByAffinity(a.asDouble(), new RestaurantId(parseUuid(id.asText())));
  }

  private static RecommendationCursor decodeByRating(JsonNode node) {
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
    return new RecommendationCursor.ByRating(avgRating, new RestaurantId(parseUuid(id.asText())));
  }

  private static RecommendationCursor decodeByName(JsonNode node) {
    JsonNode n = node.get("n");
    JsonNode id = node.get("id");
    if (n == null || !n.isTextual() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    return new RecommendationCursor.ByName(n.asText(), new RestaurantId(parseUuid(id.asText())));
  }

  private static RecommendationCursor decodeByDistance(JsonNode node) {
    JsonNode d = node.get("d");
    JsonNode id = node.get("id");
    if (d == null || !d.isNumber() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    return new RecommendationCursor.ByDistance(
        d.asDouble(), new RestaurantId(parseUuid(id.asText())));
  }

  private static RecommendationCursor decodeByCreatedAt(JsonNode node) {
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
    return new RecommendationCursor.ByCreatedAt(
        createdAt, new RestaurantId(parseUuid(id.asText())));
  }

  private static UUID parseUuid(String s) {
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException();
    }
  }

  private static boolean matchesSort(RecommendationCursor cursor, RecommendationSort sort) {
    return switch (sort) {
      case AFFINITY_DESC -> cursor instanceof RecommendationCursor.ByAffinity;
      case RATING_DESC -> cursor instanceof RecommendationCursor.ByRating;
      case NAME_ASC -> cursor instanceof RecommendationCursor.ByName;
      case DISTANCE_ASC -> cursor instanceof RecommendationCursor.ByDistance;
      case CREATED_AT_DESC -> cursor instanceof RecommendationCursor.ByCreatedAt;
    };
  }
}
