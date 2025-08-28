package com.pgu.palais_divin_back.business.repository;

import com.pgu.palais_divin_back.business.dto.RatingDto;

import java.util.Optional;

public interface RatingRepository {

    RatingDto createOrUpdateRating(String userUuid, String restaurantUuid, Integer score);

    Optional<RatingDto> findByUserAndRestaurant(String userUuid, String restaurantUuid);

    void delete(String userUuid, String restaurantUuid);
}
