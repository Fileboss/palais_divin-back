package com.pgu.palais_divin_back.business.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserSummaryDto {
    private UUID uuid;
    private String firstName;
    private String lastName;
    private String email;

    public String getUuid() {
        return uuid.toString();
    }
}
