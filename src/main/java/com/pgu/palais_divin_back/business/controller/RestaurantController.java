package com.pgu.palais_divin_back.business.controller;

import com.pgu.palais_divin_back.business.model.Restaurant;
import com.pgu.palais_divin_back.business.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    public Restaurant createRestaurant(@RequestBody Restaurant restaurant) {
        return restaurantService.createRestaurant(restaurant);
    }

    /**
     * Retrouver un restaurant avec sa note moyenne
     */
    @GetMapping("/{id}")
    public ResponseEntity<Restaurant> getRestaurantById(@PathVariable String id) {
        return restaurantService.findRestaurantByIdWithRating(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrouver tous les restaurants avec leurs notes moyennes
     */
    @GetMapping
    public List<Restaurant> getAllRestaurants() {
        return restaurantService.findAllRestaurantsWithRatings();
    }

    /**
     * Recherche de restaurants par nom
     */
    @GetMapping("/search")
    public List<Restaurant> searchRestaurants(@RequestParam String name) {
        return restaurantService.searchRestaurantsByName(name);
    }
}

