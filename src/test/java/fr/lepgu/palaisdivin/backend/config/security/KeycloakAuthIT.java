package fr.lepgu.palaisdivin.backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class KeycloakAuthIT {

  private static final String REALM = "palaisdivin";
  private static final String CLIENT_ID = "palais-divin-frontend";
  private static final String USERNAME = "testuser";
  private static final String PASSWORD = "testpassword";

  @LocalServerPort int port;

  @Autowired KeycloakContainer keycloak;

  @Test
  void authenticated_request_returns_200() {
    String token = TestKeycloakTokens.passwordGrant(keycloak, REALM, CLIENT_ID, USERNAME, PASSWORD);

    ResponseEntity<String> resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build()
            .get()
            .uri("/api/v1/user/restaurants")
            .retrieve()
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getHeaders().getContentType().toString()).startsWith("application/json");
  }

  @Test
  void unauthenticated_request_returns_401_problem_detail() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/user/restaurants")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void garbage_token_returns_401_problem_detail() {
    ResponseEntity<String> resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
            .build()
            .get()
            .uri("/api/v1/user/restaurants")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }
}
