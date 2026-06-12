package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.GeocodeMatch;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.GeocoderPort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/geocode")
class PublicGeocodeRestController {

  private final GeocoderPort geocoder;

  PublicGeocodeRestController(GeocoderPort geocoder) {
    this.geocoder = geocoder;
  }

  @GetMapping
  List<GeocodeMatch> search(
      @RequestParam("q") @NotBlank @Size(min = 2, max = 200) String q,
      @RequestParam(name = "limit", defaultValue = "5") @Min(1) @Max(10) int limit) {
    return geocoder.search(q, limit);
  }
}
