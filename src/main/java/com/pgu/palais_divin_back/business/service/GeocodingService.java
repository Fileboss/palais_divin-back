package com.pgu.palais_divin_back.business.service;

import java.util.Map;

public interface GeocodingService {
    Map<String, String> getCoordinateFromAddress(String country, String zipCode, String city, String roadAndNumber);
}
