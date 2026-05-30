package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class InvitationRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired InvitationRepositoryPort invitations;

  @Test
  void adminPostReturns201AndPersistsInvitation() {
    RestClient client = adminClient();

    ResponseEntity<InvitationResponse> resp =
        client
            .post()
            .uri("/api/v1/admin/invitations")
            .retrieve()
            .toEntity(InvitationResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    InvitationResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.id()).isNotNull();
    assertThat(body.expiresAt()).isNotNull();
    assertThat(body.signupUrl()).startsWith("http://localhost:5173/register?token=");
    assertThat(resp.getHeaders().getLocation())
        .isNotNull()
        .satisfies(
            loc -> assertThat(loc.toString()).endsWith("/api/v1/admin/invitations/" + body.id()));

    Optional<Invitation> persisted = invitations.findById(new InvitationId(body.id()));
    assertThat(persisted).isPresent();
    Invitation stored = persisted.get();
    Duration actualTtl = Duration.between(stored.createdAt(), stored.expiresAt());
    assertThat(actualTtl).isBetween(Duration.ofHours(47), Duration.ofHours(49));

    String tokenFromUrl = tokenQueryParam(body.signupUrl());
    assertThat(tokenFromUrl).isEqualTo(stored.token().value());
  }

  @Test
  void userPostReturns403ProblemDetail() {
    String userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");

    ResponseEntity<String> resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build()
            .post()
            .uri("/api/v1/admin/invitations")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/forbidden");
  }

  @Test
  void anonymousPostReturns401ProblemDetail() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/admin/invitations")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  private RestClient adminClient() {
    String adminToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testadmin", "testadmin");
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .build();
  }

  private static String tokenQueryParam(String url) {
    String query = URI.create(url).getQuery();
    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2 && "token".equals(kv[0])) {
        return kv[1];
      }
    }
    throw new AssertionError("no token query param in " + url);
  }
}
