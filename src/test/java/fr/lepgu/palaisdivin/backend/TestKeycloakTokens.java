package fr.lepgu.palaisdivin.backend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public final class TestKeycloakTokens {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TestKeycloakTokens() {}

  public static String passwordGrant(
      KeycloakContainer keycloak, String realm, String clientId, String username, String password) {
    String tokenUrl =
        keycloak.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token";

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", clientId);
    form.add("username", username);
    form.add("password", password);

    TokenResponse resp =
        RestClient.create()
            .post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);

    return resp.accessToken();
  }

  public static String subjectOf(String accessToken) {
    String[] parts = accessToken.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("not a JWT: " + accessToken);
    }
    byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
    try {
      JsonNode node = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
      JsonNode sub = node.get("sub");
      if (sub == null || sub.isNull()) {
        throw new IllegalStateException("no sub claim in JWT payload");
      }
      return sub.asText();
    } catch (Exception e) {
      throw new IllegalStateException("failed to decode JWT payload", e);
    }
  }

  record TokenResponse(@JsonProperty("access_token") String accessToken) {}
}
