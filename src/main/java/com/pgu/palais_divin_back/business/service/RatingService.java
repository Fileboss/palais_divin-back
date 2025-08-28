package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.dto.RatingDto;
import org.springframework.transaction.annotation.Transactional;

public interface RatingService {
    @Transactional
    RatingDto rateRestaurant(String userUuid, String restaurantUuid, Integer score);

    @Transactional
    void deleteRating(String userId, String restaurantId);
}
