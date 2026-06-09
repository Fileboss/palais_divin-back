package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class AdminTagRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM restaurant_tag").update();
    jdbcClient.sql("DELETE FROM tag").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
  }

  @Test
  void post_persists_and_returns_201_with_location_and_body() {
    ResponseEntity<TagResponse> resp =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagRequestPayload("FOOD", "natural-wine", "Natural wine"))
            .retrieve()
            .toEntity(TagResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    TagResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.slug()).isEqualTo("natural-wine");
    assertThat(body.label()).isEqualTo("Natural wine");
    assertThat(resp.getHeaders().getLocation())
        .isNotNull()
        .satisfies(
            uri ->
                assertThat(uri.toString()).endsWith("/api/v1/admin/tags/" + body.id().toString()));

    Long count =
        jdbcClient
            .sql("SELECT count(*) FROM tag WHERE id = :id")
            .param("id", body.id())
            .query(Long.class)
            .single();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void post_duplicateSlug_returns_409_problem() {
    adminClient()
        .post()
        .uri("/api/v1/admin/tags")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateTagRequestPayload("FOOD", "natural-wine", "Natural wine"))
        .retrieve()
        .toBodilessEntity();

    ResponseEntity<String> resp =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagRequestPayload("REGIME", "natural-wine", "Other label"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resp.getBody()).contains("/problems/conflict");
  }

  @Test
  void post_invalidSlug_returns_400_validation_problem() {
    ResponseEntity<String> resp =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagRequestPayload("FOOD", "Natural Wine", "Natural wine"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).contains("/problems/validation");
  }

  @Test
  void post_userRole_returns_403() {
    String userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");

    ResponseEntity<String> resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagRequestPayload("FOOD", "natural-wine", "Natural wine"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resp.getBody()).contains("/problems/forbidden");
  }

  @Test
  void post_anonymous_returns_401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagRequestPayload("FOOD", "natural-wine", "Natural wine"))
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(resp.getBody()).contains("/problems/unauthorized");
  }

  @Test
  void delete_existing_returns_204_and_purges_row_and_emits_event() {
    TagResponse created =
        adminClient()
            .post()
            .uri("/api/v1/admin/tags")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateTagRequestPayload("FOOD", "natural-wine", "Natural wine"))
            .retrieve()
            .body(TagResponse.class);
    jdbcClient.sql("DELETE FROM outbox_event").update();

    ResponseEntity<Void> resp =
        adminClient()
            .delete()
            .uri("/api/v1/admin/tags/{id}", created.id())
            .retrieve()
            .toBodilessEntity();

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    Long remaining =
        jdbcClient
            .sql("SELECT count(*) FROM tag WHERE id = :id")
            .param("id", created.id())
            .query(Long.class)
            .single();
    assertThat(remaining).isZero();

    Long eventCount =
        jdbcClient
            .sql(
                "SELECT count(*) FROM outbox_event"
                    + " WHERE aggregate_type = 'Tag' AND event_type = 'TagDeleted'"
                    + " AND aggregate_id = :id")
            .param("id", created.id())
            .query(Long.class)
            .single();
    assertThat(eventCount).isEqualTo(1L);
  }

  @Test
  void delete_missing_returns_404_problem() {
    ResponseEntity<String> resp =
        adminClient()
            .delete()
            .uri("/api/v1/admin/tags/{id}", UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  @Test
  void delete_userRole_returns_403() {
    String userToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testuser", "testpassword");

    ResponseEntity<String> resp =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
            .build()
            .delete()
            .uri("/api/v1/admin/tags/{id}", UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void delete_anonymous_returns_401() {
    ResponseEntity<String> resp =
        RestClient.create("http://localhost:" + port)
            .delete()
            .uri("/api/v1/admin/tags/{id}", UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

  private record CreateTagRequestPayload(String category, String slug, String label) {}
}
