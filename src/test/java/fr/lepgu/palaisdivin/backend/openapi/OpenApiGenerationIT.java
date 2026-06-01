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
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OpenApiSecurityTestConfig.class})
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
        .contains("/api/v1/user/recommendations");

    String baseDir = System.getProperty("project.basedir", System.getProperty("user.dir"));
    Path output = Path.of(baseDir, "docs", "openapi.yaml");
    Files.createDirectories(output.getParent());
    Files.writeString(output, yaml);
  }
}
