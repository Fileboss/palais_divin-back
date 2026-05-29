package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import fr.lepgu.palaisdivin.backend.user.domain.model.KeycloakUserId;
import fr.lepgu.palaisdivin.backend.user.domain.model.NewKeycloakUser;
import fr.lepgu.palaisdivin.backend.user.domain.ports.KeycloakAdminPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KeycloakAdminAdapter implements KeycloakAdminPort {

  private final KeycloakAdminClient client;
  private final KeycloakTokenSupplier tokens;

  public KeycloakAdminAdapter(KeycloakAdminClient client, KeycloakTokenSupplier tokens) {
    this.client = client;
    this.tokens = tokens;
  }

  @Override
  public KeycloakUserId createUser(NewKeycloakUser user) {
    String bearer = tokens.currentBearer();
    String id = createUserAndExtractId(user, bearer);
    assignRealmRoles(id, user.realmRoles(), bearer);
    return new KeycloakUserId(id);
  }

  private String createUserAndExtractId(NewKeycloakUser user, String bearer) {
    KeycloakUserRepresentation rep = toRepresentation(user);
    ResponseEntity<Void> response;
    try {
      response = client.createUser(bearer, rep);
    } catch (RestClientResponseException ex) {
      throw new KeycloakOperationException(
          "Keycloak createUser failed with status " + ex.getStatusCode().value(),
          ex.getStatusCode().value(),
          ex);
    } catch (RestClientException ex) {
      throw new KeycloakOperationException("Keycloak createUser transport failure", ex);
    }
    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
    if (location == null || location.isBlank()) {
      throw new KeycloakOperationException(
          "Keycloak createUser returned no Location header (status "
              + response.getStatusCode().value()
              + ")");
    }
    String id = location.substring(location.lastIndexOf('/') + 1);
    if (id.isBlank()) {
      throw new KeycloakOperationException(
          "Keycloak createUser Location header has empty id: " + location);
    }
    return id;
  }

  private void assignRealmRoles(String userId, List<String> roleNames, String bearer) {
    List<RoleRepresentation> roles = new ArrayList<>(roleNames.size());
    for (String roleName : roleNames) {
      try {
        roles.add(client.getRealmRole(bearer, roleName));
      } catch (RestClientResponseException ex) {
        throw new KeycloakOperationException(
            "Failed to resolve Keycloak realm role '"
                + roleName
                + "': status "
                + ex.getStatusCode().value(),
            ex);
      } catch (RestClientException ex) {
        throw new KeycloakOperationException(
            "Transport failure resolving Keycloak realm role '" + roleName + "'", ex);
      }
    }
    try {
      client.assignRealmRoles(bearer, userId, roles);
    } catch (RestClientResponseException ex) {
      throw new KeycloakOperationException(
          "Failed to assign Keycloak realm roles: status " + ex.getStatusCode().value(), ex);
    } catch (RestClientException ex) {
      throw new KeycloakOperationException("Transport failure assigning Keycloak realm roles", ex);
    }
  }

  private static KeycloakUserRepresentation toRepresentation(NewKeycloakUser u) {
    // Keycloak's declarative user profile (24+) requires firstName + lastName; populate both from
    // displayName since the API has no first/last split.
    return new KeycloakUserRepresentation(
        u.username(),
        u.email(),
        true,
        true,
        u.displayName(),
        u.displayName(),
        List.of(
            new KeycloakUserRepresentation.Credential("password", u.temporaryPassword(), false)));
  }
}
