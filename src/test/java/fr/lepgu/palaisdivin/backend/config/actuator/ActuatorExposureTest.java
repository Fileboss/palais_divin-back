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
    return (HttpStatus)
        http()
            .get()
            .uri(path)
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity()
            .getStatusCode();
  }

  @Test
  void health_returns_200_anon() {
    assertThat(statusOf("/actuator/health")).isEqualTo(HttpStatus.OK);
  }

  @Test
  void info_returns_200_anon() {
    assertThat(statusOf("/actuator/info")).isEqualTo(HttpStatus.OK);
  }

  @Test
  void metrics_anon_returns_401() {
    assertThat(statusOf("/actuator/metrics")).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void prometheus_anon_returns_401() {
    assertThat(statusOf("/actuator/prometheus")).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void unknown_actuator_endpoint_anon_returns_401() {
    // Security matches /actuator/** → ROLE_ADMIN before MVC resolves the endpoint, so anon
    // gets 401 (not 404). With an admin token this would surface as 404 since /env isn't exposed.
    var response =
        http()
            .get()
            .uri("/actuator/env")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getContentType())
        .isNotNull()
        .matches(t -> t.isCompatibleWith(MediaType.parseMediaType("application/problem+json")));
  }
}
