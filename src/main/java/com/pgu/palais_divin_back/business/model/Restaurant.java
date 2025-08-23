package com.pgu.palais_divin_back.business.model;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;
import java.util.UUID;

@Node
@Data
public class Restaurant {
    @Id // L'identifiant reste @Id, mais il est maintenant géré par l'application
    private UUID uuid;

    private String name;
    private String address;
    private List<String> tags;
    private Double averageRating;
    private Integer ratingCount;


    public Restaurant() {
        this.uuid = UUID.randomUUID();
    }

}
