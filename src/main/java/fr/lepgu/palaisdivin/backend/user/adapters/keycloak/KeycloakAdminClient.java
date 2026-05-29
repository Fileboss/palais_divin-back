package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface KeycloakAdminClient {

  @PostExchange(value = "/users", contentType = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> createUser(
      @RequestHeader("Authorization") String bearer, @RequestBody KeycloakUserRepresentation user);

  @GetExchange("/roles/{roleName}")
  RoleRepresentation getRealmRole(
      @RequestHeader("Authorization") String bearer, @PathVariable String roleName);

  @PostExchange(
      value = "/users/{userId}/role-mappings/realm",
      contentType = MediaType.APPLICATION_JSON_VALUE)
  void assignRealmRoles(
      @RequestHeader("Authorization") String bearer,
      @PathVariable String userId,
      @RequestBody List<RoleRepresentation> roles);
}
