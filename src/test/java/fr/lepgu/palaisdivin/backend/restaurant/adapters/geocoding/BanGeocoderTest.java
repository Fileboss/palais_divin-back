package fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import fr.lepgu.palaisdivin.backend.restaurant.domain.GeocodeFailedException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.GeocodeMatch;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class BanGeocoderTest {

  private static final String BASE_URL = "https://api-adresse.data.gouv.fr";

  private MockRestServiceServer server;
  private BanGeocoder geocoder;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    BanApiClient client =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(BanApiClient.class);
    geocoder = new BanGeocoder(client);
  }

  @Test
  void geocodeParsesGeoJsonAndReturnsCoordinatesWithin50mOfExpected() {
    // BAN returns GeoJSON [lon, lat]. Expected Septime address ~ (48.8534, 2.3795).
    String body =
        """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [2.379533, 48.853377] },
              "properties": {
                "label": "80 Rue de Charonne 75011 Paris",
                "score": 0.96
              }
            }
          ]
        }
        """;
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/search")))
        .andExpect(method(org.springframework.http.HttpMethod.GET))
        .andExpect(queryParam("q", "80%20rue%20de%20charonne%2C%2075011%20paris"))
        .andExpect(queryParam("limit", "1"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Coordinates result = geocoder.geocode("  80 Rue de Charonne,\t75011 Paris  ");

    double expectedLat = 48.8534;
    double expectedLon = 2.3795;
    assertThat(haversineMeters(result.latitude(), result.longitude(), expectedLat, expectedLon))
        .isLessThan(50.0);

    server.verify();
  }

  @Test
  void geocodeEmptyFeaturesThrowsUnresolvableAddressException() {
    String body =
        """
        { "type": "FeatureCollection", "features": [] }
        """;
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/search")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> geocoder.geocode("zzzz unknown address zzzz"))
        .isInstanceOf(UnresolvableAddressException.class)
        .hasMessageContaining("zzzz unknown address zzzz");

    server.verify();
  }

  @Test
  void searchReturnsAllFeaturesInOrderWithLabelAndCoords() {
    String body =
        """
        {
          "type": "FeatureCollection",
          "features": [
            { "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [2.3795, 48.8534] },
              "properties": { "label": "80 Rue de Charonne 75011 Paris", "score": 0.96 } },
            { "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [2.3522, 48.8566] },
              "properties": { "label": "Paris", "score": 0.42 } }
          ]
        }
        """;
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/search")))
        .andExpect(queryParam("q", "tour%20eiffel"))
        .andExpect(queryParam("limit", "5"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    List<GeocodeMatch> matches = geocoder.search("Tour Eiffel", 5);

    assertThat(matches).hasSize(2);
    assertThat(matches.get(0).label()).isEqualTo("80 Rue de Charonne 75011 Paris");
    assertThat(matches.get(0).latitude()).isEqualTo(48.8534);
    assertThat(matches.get(0).longitude()).isEqualTo(2.3795);
    assertThat(matches.get(1).label()).isEqualTo("Paris");
    server.verify();
  }

  @Test
  void searchBlankQueryReturnsEmptyListWithoutCallingBan() {
    List<GeocodeMatch> matches = geocoder.search("   ", 5);

    assertThat(matches).isEmpty();
    server.verify();
  }

  @Test
  void searchUpstreamFailureWrapsAsGeocodeFailedException() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/search")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> geocoder.search("anywhere", 5))
        .isInstanceOf(GeocodeFailedException.class);

    server.verify();
  }

  @Test
  void normalizeTrimsLowercasesAndCollapsesWhitespace() {
    assertThat(BanGeocoder.normalize("  80   Rue de Charonne\t75011 Paris  "))
        .isEqualTo("80 rue de charonne 75011 paris");
    assertThat(BanGeocoder.normalize(null)).isEmpty();
    assertThat(BanGeocoder.normalize("   ")).isEmpty();
  }

  private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double earthRadiusMeters = 6_371_000.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return earthRadiusMeters * c;
  }
}
