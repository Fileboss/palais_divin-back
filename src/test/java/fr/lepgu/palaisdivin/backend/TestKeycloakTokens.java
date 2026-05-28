package fr.lepgu.palaisdivin.backend;

import com.fasterxml.jackson.annotation.JsonProperty;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public final class TestKeycloakTokens {

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

  record TokenResponse(@JsonProperty("access_token") String accessToken) {}
}
