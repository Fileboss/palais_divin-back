package fr.lepgu.palaisdivin.backend.config.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.endpoint.prometheus.access=read-only",
      "management.prometheus.metrics.export.enabled=true"
    })
@ImportAutoConfiguration(PrometheusMetricsExportAutoConfiguration.class)
@Import(TestcontainersConfiguration.class)
class ActuatorExposureTest {

  @LocalServerPort int port;

  private RestClient http() {
    return RestClient.create("http://localhost:" + port);
  }

  private HttpStatus statusOf(String path) {
    return (HttpStatus) http().get().uri(path).retrieve().toBodilessEntity().getStatusCode();
  }

  @Test
  void health_returns_200() {
    assertThat(statusOf("/actuator/health")).isEqualTo(HttpStatus.OK);
  }

  @Test
  void info_returns_200() {
    assertThat(statusOf("/actuator/info")).isEqualTo(HttpStatus.OK);
  }

  @Test
  void metrics_returns_200() {
    assertThat(statusOf("/actuator/metrics")).isEqualTo(HttpStatus.OK);
  }

  @Test
  void prometheus_returns_200() {
    assertThat(statusOf("/actuator/prometheus")).isEqualTo(HttpStatus.OK);
  }

  @Test
  void env_is_not_exposed_returns_404_problem_detail() {
    var response =
        http()
            .get()
            .uri("/actuator/env")
            .retrieve()
            .onStatus(s -> s.value() == 404, (req, res) -> {})
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType())
        .isNotNull()
        .matches(t -> t.isCompatibleWith(MediaType.parseMediaType("application/problem+json")));
  }
}
