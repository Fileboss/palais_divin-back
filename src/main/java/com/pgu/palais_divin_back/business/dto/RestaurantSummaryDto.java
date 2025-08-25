package com.pgu.palais_divin_back.business.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RestaurantSummaryDto {
    private UUID uuid;
    private String name;

    public String getUuid() {
        return uuid.toString();
    }
}
