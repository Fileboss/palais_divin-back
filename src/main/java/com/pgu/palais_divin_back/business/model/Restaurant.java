package com.pgu.palais_divin_back.business.model;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;
import java.util.UUID;

@Node
@Data
public class Restaurant {
    @Id
    private UUID uuid;

    private String name;
    private String country;
    private String zipCode;
    private String city;
    private String roadAndNumber;
    private String additionalInformation;
    private Double latitude;
    private Double longitude;
    private List<String> tags;
    private Double averageRating;
    private Integer ratingCount;
    private String photoUrl;

    public Restaurant() {
        this.uuid = UUID.randomUUID();
    }
}
