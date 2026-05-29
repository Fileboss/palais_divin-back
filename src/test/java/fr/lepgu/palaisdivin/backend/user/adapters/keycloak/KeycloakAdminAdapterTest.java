package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import fr.lepgu.palaisdivin.backend.user.domain.model.KeycloakUserId;
import fr.lepgu.palaisdivin.backend.user.domain.model.NewKeycloakUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

class KeycloakAdminAdapterTest {

  private static final String BEARER = "Bearer abc-123";
  private static final String CREATED_USER_ID = "8b8e7c00-1234-4abc-9def-000000000001";
  private static final RoleRepresentation USER_ROLE =
      new RoleRepresentation("role-id-user", "USER");

  private KeycloakAdminClient client;
  private KeycloakTokenSupplier tokens;
  private KeycloakAdminAdapter adapter;

  @BeforeEach
  void setUp() {
    client = Mockito.mock(KeycloakAdminClient.class);
    tokens = Mockito.mock(KeycloakTokenSupplier.class);
    adapter = new KeycloakAdminAdapter(client, tokens);
    when(tokens.currentBearer()).thenReturn(BEARER);
  }

  @Test
  void createsUserAndAssignsRequestedRealmRoles() {
    stubCreateSuccess();
    when(client.getRealmRole(eq(BEARER), eq("USER"))).thenReturn(USER_ROLE);

    KeycloakUserId id = adapter.createUser(newUser());

    assertThat(id).isEqualTo(new KeycloakUserId(CREATED_USER_ID));
    verify(client).getRealmRole(BEARER, "USER");
    verify(client).assignRealmRoles(BEARER, CREATED_USER_ID, List.of(USER_ROLE));
  }

  @Test
  void forwardsBearerAndMapsCreatePayload() {
    stubCreateSuccess();
    when(client.getRealmRole(eq(BEARER), eq("USER"))).thenReturn(USER_ROLE);

    adapter.createUser(newUser());

    ArgumentCaptor<KeycloakUserRepresentation> captor =
        ArgumentCaptor.forClass(KeycloakUserRepresentation.class);
    verify(client).createUser(eq(BEARER), captor.capture());
    KeycloakUserRepresentation sent = captor.getValue();
    assertThat(sent.username()).isEqualTo("kc-user");
    assertThat(sent.email()).isEqualTo("kc-user@example.com");
    assertThat(sent.firstName()).isEqualTo("Kc User");
    assertThat(sent.enabled()).isTrue();
    assertThat(sent.emailVerified()).isTrue();
    assertThat(sent.credentials()).hasSize(1);
    KeycloakUserRepresentation.Credential credential = sent.credentials().get(0);
    assertThat(credential.type()).isEqualTo("password");
    assertThat(credential.value()).isEqualTo("temp-pass");
    assertThat(credential.temporary()).isTrue();
  }

  @Test
  void missingLocationHeaderRaisesKeycloakOperationException() {
    when(client.createUser(eq(BEARER), any()))
        .thenReturn(new ResponseEntity<>(new HttpHeaders(), HttpStatus.CREATED));

    assertThatThrownBy(() -> adapter.createUser(newUser()))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("no Location header");
  }

  @Test
  void createConflictSurfacesAsKeycloakOperationException() {
    when(client.createUser(eq(BEARER), any()))
        .thenThrow(
            new RestClientResponseException(
                "409 Conflict", HttpStatus.CONFLICT, "Conflict", null, null, null));

    assertThatThrownBy(() -> adapter.createUser(newUser()))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("409");
  }

  @Test
  void createTransportFailureSurfacesAsKeycloakOperationException() {
    when(client.createUser(eq(BEARER), any())).thenThrow(new RestClientException("timeout"));

    assertThatThrownBy(() -> adapter.createUser(newUser()))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("transport failure");
  }

  @Test
  void roleLookupFailureSurfacesAsKeycloakOperationException() {
    stubCreateSuccess();
    when(client.getRealmRole(eq(BEARER), eq("USER")))
        .thenThrow(
            new RestClientResponseException(
                "404 Not Found", HttpStatus.NOT_FOUND, "Not Found", null, null, null));

    assertThatThrownBy(() -> adapter.createUser(newUser()))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("'USER'")
        .hasMessageContaining("404");
  }

  private void stubCreateSuccess() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.LOCATION, "http://kc/admin/realms/palaisdivin/users/" + CREATED_USER_ID);
    when(client.createUser(eq(BEARER), any()))
        .thenReturn(new ResponseEntity<>(headers, HttpStatus.CREATED));
  }

  private static NewKeycloakUser newUser() {
    return new NewKeycloakUser(
        "kc-user", "kc-user@example.com", "Kc User", "temp-pass", List.of("USER"));
  }
}
