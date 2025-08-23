package com.pgu.palais_divin_back.business.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserWithRatedRestaurantsDto {
    private String uuid;
    private String firstName;
    private String lastName;
    private String email;
    private List<RestaurantSummaryDto> ratedRestaurants;
}