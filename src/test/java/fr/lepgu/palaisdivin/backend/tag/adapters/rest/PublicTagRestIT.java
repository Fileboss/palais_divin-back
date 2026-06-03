package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.TestKeycloakTokens;
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

class PublicTagRestIT extends AbstractIntegrationTest {

  private static final String REALM = "palaisdivin";
  private static final String FRONTEND_CLIENT = "palais-divin-frontend";

  @LocalServerPort int port;
  @Autowired KeycloakContainer keycloak;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM restaurant_tag").update();
    jdbcClient.sql("DELETE FROM tag").update();
  }

  @Test
  void empty_catalog_returns_200_with_four_empty_groups() {
    ResponseEntity<TagCatalogResponse> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/public/tags")
            .retrieve()
            .toEntity(TagCatalogResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagCatalogResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.groups()).hasSize(4);
    assertThat(body.groups()).allSatisfy(g -> assertThat(g.tags()).isEmpty());
  }

  @Test
  void populated_catalog_groups_by_category_in_enum_order() {
    seedTag("FOOD", "natural-wine", "Natural wine");
    seedTag("FOOD", "burger", "Burger");
    seedTag("REGIME", "vegan", "Vegan");
    seedTag("VENUE_TYPE", "bistro", "Bistrot");

    ResponseEntity<TagCatalogResponse> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/public/tags")
            .retrieve()
            .toEntity(TagCatalogResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagCatalogResponse body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.groups()).hasSize(4);

    assertThat(body.groups().get(0).category().name()).isEqualTo("FOOD");
    assertThat(body.groups().get(0).tags())
        .extracting(TagResponse::slug)
        .containsExactly("burger", "natural-wine");

    assertThat(body.groups().get(1).category().name()).isEqualTo("REGIME");
    assertThat(body.groups().get(1).tags()).extracting(TagResponse::slug).containsExactly("vegan");

    assertThat(body.groups().get(2).category().name()).isEqualTo("PLACE");
    assertThat(body.groups().get(2).tags()).isEmpty();

    assertThat(body.groups().get(3).category().name()).isEqualTo("VENUE_TYPE");
    assertThat(body.groups().get(3).tags()).extracting(TagResponse::slug).containsExactly("bistro");
  }

  @Test
  void anonymous_allowed() {
    ResponseEntity<TagCatalogResponse> resp =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/public/tags")
            .retrieve()
            .toEntity(TagCatalogResponse.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private void seedTag(String category, String slug, String label) {
    String adminToken =
        TestKeycloakTokens.passwordGrant(
            keycloak, REALM, FRONTEND_CLIENT, "testadmin", "testadmin");
    RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .build()
        .post()
        .uri("/api/v1/admin/tags")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateTagRequestPayload(category, slug, label))
        .retrieve()
        .toBodilessEntity();
  }

  private record CreateTagRequestPayload(String category, String slug, String label) {}
}
