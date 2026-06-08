package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class PhotoCursorCodec {

  private static final int V = 1;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PhotoCursorCodec() {}

  static String encode(PhotoCursor cursor) {
    try {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("c", cursor.createdAt().toString());
      node.put("id", cursor.id().toString());
      node.put("v", V);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IllegalStateException("failed to encode cursor", e);
    }
  }

  static PhotoCursor decode(String token) {
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
    if (v == null || !v.isInt() || v.asInt() != V) {
      throw new InvalidCursorException();
    }
    JsonNode c = node.get("c");
    JsonNode id = node.get("id");
    if (c == null || !c.isTextual() || id == null || !id.isTextual()) {
      throw new InvalidCursorException();
    }
    Instant createdAt;
    try {
      createdAt = Instant.parse(c.asText());
    } catch (DateTimeParseException e) {
      throw new InvalidCursorException();
    }
    UUID parsedId;
    try {
      parsedId = UUID.fromString(id.asText());
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException();
    }
    return new PhotoCursor(createdAt, parsedId);
  }
}
