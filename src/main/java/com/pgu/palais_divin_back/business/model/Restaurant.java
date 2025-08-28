package com.pgu.palais_divin_back.business.model;

import jakarta.validation.constraints.*;
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

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String country;

    @NotBlank
    @Pattern(regexp = "\\D{5}$")
    private String zipCode;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 255)
    private String roadAndNumber;

    @Size(max = 500)
    private String additionalInformation;


    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double longitude;


    @NotEmpty(message = "Add at least one tag when creating a new restaurant")
    private List<@NotBlank String> tags;

    @Min(value = 0)
    @Max(value = 10)
    private Double averageRating;

    @Min(value = 0)
    private Integer ratingCount;

    private String photoUrl;

    public Restaurant() {
        this.uuid = UUID.randomUUID();
    }
}
