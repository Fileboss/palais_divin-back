package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.exception.GeoCodingException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, String> getCoordinateFromAddress(String country, String zipCode, String city, String roadAndNumber) {
        try {
            String url = "https://nominatim.openstreetmap.org/search" +
                    "?street=" + URLEncoder.encode(roadAndNumber, StandardCharsets.UTF_8) +
                    "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8) +
                    "&postalcode=" + URLEncoder.encode(zipCode, StandardCharsets.UTF_8) +
                    "&country=" + URLEncoder.encode(country, StandardCharsets.UTF_8) +
                    "&format=json&limit=1";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "palais-divin-app/1.0 (contact@example.com)");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> response = responseEntity.getBody();

            Map<String, String> geoInfo = new HashMap<>();
            if (response != null && !response.isEmpty()) {
                Map<String, Object> firstResult = response.get(0);
                geoInfo.put("latitude", (String) firstResult.get("lat"));
                geoInfo.put("longitude", (String) firstResult.get("lon"));
            } else {
                throw new GeoCodingException("Adresse introuvable : " + url);
            }

            return geoInfo;
        } catch (Exception e) {
            throw new GeoCodingException("Erreur lors de l'appel à Nominatim", e);
        }
    }

}