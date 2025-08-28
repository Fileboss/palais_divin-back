package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.model.Restaurant;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface RestaurantService {
    Restaurant createRestaurant(Restaurant restaurant);

    @Transactional(readOnly = true)
    Optional<Restaurant> findRestaurantByIdWithRating(String restaurantUuid);

    @Transactional(readOnly = true)
    List<Restaurant> findAllRestaurantsWithRatings();

    @Transactional(readOnly = true)
    List<Restaurant> searchRestaurantsByName(String name);

    @Transactional
    Restaurant addPhotoToRestaurant(String restaurantUuid, MultipartFile photoFile);

    @Transactional
    Restaurant removePhotoFromRestaurant(String restaurantUuid);
}
