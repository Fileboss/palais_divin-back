package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.model.Restaurant;
import com.pgu.palais_divin_back.business.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor // Lombok injecte le repository dans le constructeur
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;

    public Restaurant createRestaurant(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }

    /**
     * Version simple pour les besoins internes
     */
    @Transactional(readOnly = true)
    public Optional<Restaurant> findRestaurantById(String id) {
        return restaurantRepository.findById(UUID.fromString(id));
    }

    /**
     * Retrouver un restaurant à partir de son restaurantUuid :
     * renvoyer toutes les infos + la note moyenne obtenue par le resto
     */
    @Transactional(readOnly = true)
    public Optional<Restaurant> findRestaurantByIdWithRating(String restaurantUuid) {
        return restaurantRepository.findById(UUID.fromString(restaurantUuid));
    }

    /**
     * Retrouver tous les restaurants, avec toutes leurs infos y compris leur note moyenne
     */
    @Transactional(readOnly = true)
    public List<Restaurant> findAllRestaurantsWithRatings() {
        return restaurantRepository.findAll();
    }

    /**
     * Retrouver n restaurant à partir d'une chaîne de caractères correspondant au nom (pour recherche) :
     * renvoyer les infos de base
     */
    @Transactional(readOnly = true)
    public List<Restaurant> searchRestaurantsByName(String name) {
        return restaurantRepository.findByNameContainingIgnoreCase(name);
    }
}