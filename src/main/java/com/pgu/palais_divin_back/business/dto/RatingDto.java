package com.pgu.palais_divin_back.business.dto;

import lombok.Data;

import java.time.OffsetDateTime;

// Le DTO renvoyé par l'API
@Data
public class RatingDto {
    private String id;                // elementId(rel)
    private Integer score;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UserSummaryDto user;
    private RestaurantSummaryDto restaurant;
}

