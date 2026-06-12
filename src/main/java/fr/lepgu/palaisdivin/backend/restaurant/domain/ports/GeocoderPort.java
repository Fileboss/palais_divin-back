package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.GeocodeMatch;
import java.util.List;

public interface GeocoderPort {

  Coordinates geocode(String address);

  List<GeocodeMatch> search(String query, int limit);
}
