package fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding;

import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.GeocoderPort;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class BanGeocoder implements GeocoderPort {

  static final String CACHE_NAME = "geocode";

  private final BanApiClient client;

  public BanGeocoder(BanApiClient client) {
    this.client = client;
  }

  @Override
  @Cacheable(
      cacheNames = CACHE_NAME,
      key =
          "T(fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanGeocoder).normalize(#address)")
  public Coordinates geocode(String address) {
    String query = normalize(address);
    if (query.isEmpty()) {
      throw new UnresolvableAddressException(address);
    }
    BanResponse response = client.search(query, 1);
    if (response == null || response.features() == null || response.features().isEmpty()) {
      throw new UnresolvableAddressException(address);
    }
    BanResponse.Feature feature = response.features().get(0);
    List<Double> coords = feature.geometry() == null ? null : feature.geometry().coordinates();
    if (coords == null || coords.size() < 2) {
      throw new UnresolvableAddressException(address);
    }
    return new Coordinates(coords.get(1), coords.get(0));
  }

  public static String normalize(String input) {
    if (input == null) {
      return "";
    }
    return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
