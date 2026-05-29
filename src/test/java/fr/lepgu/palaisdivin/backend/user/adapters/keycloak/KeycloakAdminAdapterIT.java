package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import fr.lepgu.palaisdivin.backend.user.domain.model.KeycloakUserId;
import fr.lepgu.palaisdivin.backend.user.domain.model.NewKeycloakUser;
import fr.lepgu.palaisdivin.backend.user.domain.ports.KeycloakAdminPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KeycloakAdminAdapterIT {

  private static final String REALM = "palaisdivin";

  @Autowired KeycloakAdminPort adapter;
  @Autowired KeycloakContainer keycloak;

  @Test
  void createsUserInKeycloakWithRequestedRealmRole() {
    String username = "kc-" + UUID.randomUUID();
    NewKeycloakUser request =
        new NewKeycloakUser(
            username, username + "@example.com", "Test Created", "temp-pass", List.of("USER"));

    KeycloakUserId created = adapter.createUser(request);

    assertThat(created).isNotNull();
    assertThat(created.value()).isNotBlank();
    List<UserRepresentation> found = fetchUsersByUsername(username);
    assertThat(found).hasSize(1);
    UserRepresentation user = found.get(0);
    assertThat(user.username()).isEqualTo(username);
    assertThat(user.email()).isEqualTo(username + "@example.com");
    assertThat(user.id()).isEqualTo(created.value());
    assertThat(realmRoleNamesFor(user.id())).contains("USER");
  }

  @Test
  void duplicateUsernameSurfacesAsKeycloakOperationException() {
    String username = "kc-dup-" + UUID.randomUUID();
    NewKeycloakUser request =
        new NewKeycloakUser(
            username, username + "@example.com", "Dup", "temp-pass", List.of("USER"));
    adapter.createUser(request);

    assertThatThrownBy(() -> adapter.createUser(request))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("409");
  }

  private List<UserRepresentation> fetchUsersByUsername(String username) {
    String adminToken = adminAccessToken();
    return List.of(
        java.util.Objects.requireNonNull(
            RestClient.builder()
                .baseUrl(keycloak.getAuthServerUrl() + "/admin/realms/" + REALM)
                .defaultHeader("Authorization", "Bearer " + adminToken)
                .build()
                .get()
                .uri(b -> b.path("/users").queryParam("username", username).build())
                .retrieve()
                .body(UserRepresentation[].class)));
  }

  private List<String> realmRoleNamesFor(String userId) {
    String adminToken = adminAccessToken();
    RoleRepresentation[] roles =
        RestClient.builder()
            .baseUrl(keycloak.getAuthServerUrl() + "/admin/realms/" + REALM)
            .defaultHeader("Authorization", "Bearer " + adminToken)
            .build()
            .get()
            .uri("/users/{id}/role-mappings/realm", userId)
            .retrieve()
            .body(RoleRepresentation[].class);
    return roles == null
        ? List.of()
        : java.util.Arrays.stream(roles).map(RoleRepresentation::name).toList();
  }

  private String adminAccessToken() {
    org.springframework.util.MultiValueMap<String, String> form =
        new org.springframework.util.LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", "palais-divin-backend");
    form.add("client_secret", "test-backend-secret");
    TokenPayload payload =
        RestClient.create()
            .post()
            .uri(
                keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
            .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenPayload.class);
    return java.util.Objects.requireNonNull(payload).accessToken();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenPayload(
      @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record UserRepresentation(String id, String username, String email) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RoleRepresentation(String name) {}
}
