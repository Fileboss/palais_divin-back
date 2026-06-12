package fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding;

import fr.lepgu.palaisdivin.backend.restaurant.domain.GeocodeFailedException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.GeocodeMatch;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.GeocoderPort;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

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
    BanResponse response = callBan(query, 1);
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

  @Override
  @Cacheable(
      cacheNames = CACHE_NAME,
      key =
          "'search:' + #limit + ':' +"
              + " T(fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanGeocoder).normalize(#query)")
  public List<GeocodeMatch> search(String query, int limit) {
    String normalized = normalize(query);
    if (normalized.isEmpty()) {
      return List.of();
    }
    BanResponse response = callBan(normalized, limit);
    if (response == null || response.features() == null) {
      return List.of();
    }
    return response.features().stream()
        .map(BanGeocoder::toMatch)
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private BanResponse callBan(String query, int limit) {
    try {
      return client.search(query, limit);
    } catch (RestClientException e) {
      throw new GeocodeFailedException("BAN geocoding request failed", e);
    }
  }

  private static GeocodeMatch toMatch(BanResponse.Feature f) {
    List<Double> coords = f.geometry() == null ? null : f.geometry().coordinates();
    if (coords == null || coords.size() < 2) {
      return null;
    }
    String label = f.properties() == null ? null : f.properties().label();
    return new GeocodeMatch(label, coords.get(1), coords.get(0));
  }

  public static String normalize(String input) {
    if (input == null) {
      return "";
    }
    return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
