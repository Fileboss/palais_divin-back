package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.dto.RatingDto;
import com.pgu.palais_divin_back.business.repository.RatingRepository;
import com.pgu.palais_divin_back.business.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private final RatingRepository ratingRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Noter un restaurant - recalcule automatiquement la moyenne
     */
    @Transactional
    public RatingDto rateRestaurant(String userUuid, String restaurantUuid, Integer score) {
        // Validation
        if (score < 1 || score > 10) {
            throw new IllegalArgumentException("La note doit être comprise entre 1 et 10");
        }

        log.info("User {} rating restaurant {} with score {}", userUuid, restaurantUuid, score);

        // Créer ou mettre à jour la note
        RatingDto rating = ratingRepository.createOrUpdateRating(userUuid, restaurantUuid, score);

        // Recalculer automatiquement la moyenne
        updateRestaurantRating(restaurantUuid);

        return rating;
    }


    @Transactional
    public void deleteRating(String userId, String restaurantId) {
        log.info("Deleting rating for user {} and restaurant {}", userId, restaurantId);
        ratingRepository.delete(userId, restaurantId);
        updateRestaurantRating(restaurantId);
    }

    private void updateRestaurantRating(String restaurantId) {
        log.info("Updating average rating for restaurant {}", restaurantId);
        restaurantRepository.updateRestaurantRating(restaurantId);
    }
}