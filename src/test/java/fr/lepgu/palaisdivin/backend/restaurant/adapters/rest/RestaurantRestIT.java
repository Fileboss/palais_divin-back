package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RestaurantRestIT {

  @LocalServerPort int port;

  @Test
  void postThenGetReturnsTheSameRestaurant() {
    RestClient client = RestClient.create("http://localhost:" + port);

    CreateRestaurantRequest req =
        new CreateRestaurantRequest(
            "Septime", "80 Rue de Charonne", new CoordinatesDto(48.8536, 2.3795));

    ResponseEntity<RestaurantResponse> postResp =
        client
            .post()
            .uri("/api/v1/public/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(req)
            .retrieve()
            .toEntity(RestaurantResponse.class);

    assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(postResp.getHeaders().getLocation()).isNotNull();
    RestaurantResponse created = postResp.getBody();
    assertThat(created).isNotNull();
    assertThat(created.id()).isNotNull();
    assertThat(created.name()).isEqualTo("Septime");
    assertThat(created.address()).isEqualTo("80 Rue de Charonne");
    assertThat(created.location().latitude()).isEqualTo(48.8536);
    assertThat(created.location().longitude()).isEqualTo(2.3795);

    RestaurantResponse fetched =
        client
            .get()
            .uri(postResp.getHeaders().getLocation())
            .retrieve()
            .body(RestaurantResponse.class);

    assertThat(fetched).isNotNull();
    assertThat(fetched.id()).isEqualTo(created.id());
    assertThat(fetched.name()).isEqualTo(created.name());
    assertThat(fetched.address()).isEqualTo(created.address());
    assertThat(fetched.location()).isEqualTo(created.location());
    assertThat(fetched.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.MILLIS));
  }
}
