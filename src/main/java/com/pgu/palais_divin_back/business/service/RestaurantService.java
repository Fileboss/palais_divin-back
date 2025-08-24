package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.model.Restaurant;
import com.pgu.palais_divin_back.business.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor // Lombok injecte le repository dans le constructeur
@Slf4j
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;
    private final FileStorageService fileStorageService;

    public Restaurant createRestaurant(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }

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

    @Transactional
    public Restaurant addPhotoToRestaurant(String restaurantUuid, MultipartFile photoFile) {
        // Vérifier que le restaurant existe
        Restaurant restaurant = restaurantRepository.findById(UUID.fromString(restaurantUuid))
                .orElseThrow(() -> new IllegalArgumentException("Restaurant non trouvé: " + restaurantUuid));

        // Supprimer l'ancienne photo si elle existe
        if (restaurant.getPhotoUrl() != null && !restaurant.getPhotoUrl().isEmpty()) {
            try {
                fileStorageService.deleteRestaurantPhoto(restaurant.getPhotoUrl());
            } catch (Exception e) {
                log.warn("Impossible de supprimer l'ancienne photo: {}", restaurant.getPhotoUrl(), e);
            }
        }

        // Upload de la nouvelle photo
        String photoUrl = fileStorageService.uploadRestaurantPhoto(restaurantUuid, photoFile);

        // Mettre à jour le restaurant
        restaurant.setPhotoUrl(photoUrl);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Supprimer la photo d'un restaurant
     */
    @Transactional
    public Restaurant removePhotoFromRestaurant(String restaurantUuid) {
        Restaurant restaurant = restaurantRepository.findById(UUID.fromString(restaurantUuid))
                .orElseThrow(() -> new IllegalArgumentException("Restaurant non trouvé: " + restaurantUuid));

        if (restaurant.getPhotoUrl() != null && !restaurant.getPhotoUrl().isEmpty()) {
            // Supprimer la photo du stockage
            fileStorageService.deleteRestaurantPhoto(restaurant.getPhotoUrl());

            // Mettre à jour le restaurant
            restaurant.setPhotoUrl(null);
            return restaurantRepository.save(restaurant);
        }

        return restaurant;
    }
}