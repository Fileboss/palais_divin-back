package com.pgu.palais_divin_back.business.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class RatingDto {
    private String id;
    private Integer score;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UserSummaryDto user;
    private RestaurantSummaryDto restaurant;
}

