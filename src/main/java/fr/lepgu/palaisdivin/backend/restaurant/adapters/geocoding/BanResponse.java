package fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BanResponse(List<Feature> features) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Feature(Geometry geometry, Properties properties) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Geometry(List<Double> coordinates) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Properties(Double score, String label) {}
}
