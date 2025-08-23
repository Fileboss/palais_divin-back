// RestaurantSearchDto.java  
package com.pgu.palais_divin_back.business.dto;

import lombok.Data;
import java.util.List;

@Data
public class RestaurantSearchDto {
    private Long id;
    private String name;
    private String address;
    private List<String> tags;
}