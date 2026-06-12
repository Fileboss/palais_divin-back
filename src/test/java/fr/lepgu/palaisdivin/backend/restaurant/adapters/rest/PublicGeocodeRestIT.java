package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanResponse;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.GeocodeMatch;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class PublicGeocodeRestIT extends AbstractIntegrationTest {

  @LocalServerPort int port;
  @Autowired SharedTestStubs.BanApiClientStub banStub;

  @AfterEach
  void resetStub() {
    banStub.reset();
  }

  @Test
  void returnsAllFeaturesAsGeocodeMatches() {
    banStub.setDefault(
        new BanResponse(
            List.of(
                new BanResponse.Feature(
                    new BanResponse.Geometry(List.of(2.2945, 48.8584)),
                    new BanResponse.Properties(0.95, "Tour Eiffel 75007 Paris")),
                new BanResponse.Feature(
                    new BanResponse.Geometry(List.of(2.3522, 48.8566)),
                    new BanResponse.Properties(0.40, "Paris")))));

    List<GeocodeMatch> body =
        client()
            .get()
            .uri("/api/v1/public/geocode?q={q}&limit=5", "Tour Eiffel")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

    assertThat(body).hasSize(2);
    assertThat(body.get(0).label()).isEqualTo("Tour Eiffel 75007 Paris");
    assertThat(body.get(0).latitude()).isEqualTo(48.8584);
    assertThat(body.get(0).longitude()).isEqualTo(2.2945);
  }

  @Test
  void blankQueryReturns400() {
    ResponseEntity<String> resp =
        client()
            .get()
            .uri("/api/v1/public/geocode?q=")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void limitTooLargeReturns400() {
    ResponseEntity<String> resp =
        client()
            .get()
            .uri("/api/v1/public/geocode?q={q}&limit=50", "Paris")
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private RestClient client() {
    return RestClient.create("http://localhost:" + port);
  }
}
