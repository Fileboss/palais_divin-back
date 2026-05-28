package fr.lepgu.palaisdivin.backend.restaurant.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;

public interface GeocoderPort {

  Coordinates geocode(String address);
}
