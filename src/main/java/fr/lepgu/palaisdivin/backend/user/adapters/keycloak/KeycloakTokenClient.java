package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface KeycloakTokenClient {

  @PostExchange(
      value = "/protocol/openid-connect/token",
      contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  TokenResponse fetch(
      @RequestParam("grant_type") String grantType,
      @RequestParam("client_id") String clientId,
      @RequestParam("client_secret") String clientSecret);
}
