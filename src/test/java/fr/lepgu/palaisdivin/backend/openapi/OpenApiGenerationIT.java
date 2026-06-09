package fr.lepgu.palaisdivin.backend.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OpenApiSecurityTestConfig.class})
@ActiveProfiles("test")
class OpenApiGenerationIT {

  @LocalServerPort int port;

  @Test
  void writeOpenApiYamlToDocsDir() throws IOException {
    String yaml =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/v3/api-docs.yaml")
            .retrieve()
            .body(String.class);

    assertThat(yaml)
        .isNotBlank()
        .contains("/api/v1/public/ping")
        .contains("/api/v1/public/restaurants")
        .contains("/api/v1/public/restaurants/{id}")
        .contains("/api/v1/public/restaurants/{restaurantId}/reviews")
        .contains("/api/v1/user/restaurants")
        .contains("/api/v1/user/restaurants/{id}")
        .contains("/api/v1/user/recommendations")
        .contains("/api/v1/user/restaurants/{id}/affinity")
        .contains("/api/v1/user/restaurants/{restaurantId}/photos/upload-url")
        .contains("/api/v1/user/restaurants/{restaurantId}/photos")
        .contains("/api/v1/user/restaurants/{restaurantId}/photos/{photoId}/download-url")
        .contains("/api/v1/admin/restaurants/{id}")
        .contains("/api/v1/admin/tags")
        .contains("/api/v1/public/tags")
        .contains("/api/v1/user/restaurants/{restaurantId}/tags/{tagId}")
        .contains("/api/v1/public/users/{userId}")
        .contains("/api/v1/public/users/{userId}/reviews")
        .contains("/api/v1/public/restaurants/{restaurantId}/photos");
    // M9.3: tag query param on the public list
    assertThat(yaml).contains("name: tag");
    // I4.1: authorDisplayName on the review response shape
    assertThat(yaml).contains("authorDisplayName");
    // I7.1: thumbnail on the restaurant response shape
    assertThat(yaml).contains("thumbnail");
    // I7.2: avgRating/distanceMetres on the recommendation response shape
    assertThat(yaml).contains("RecommendationResponse");
    // I7.3: AFFINITY_DESC sort enum on the public restaurant list
    assertThat(yaml).contains("AFFINITY_DESC");
    // I8.1: reviewCount on the restaurant detail response
    assertThat(yaml).contains("reviewCount");
    // I8.3: admin DELETE on /admin/tags/{tagId}
    assertThat(yaml).contains("/api/v1/admin/tags/{tagId}");

    String baseDir = System.getProperty("project.basedir", System.getProperty("user.dir"));
    Path output = Path.of(baseDir, "docs", "openapi.yaml");
    Files.createDirectories(output.getParent());
    Files.writeString(output, yaml);
  }
}
