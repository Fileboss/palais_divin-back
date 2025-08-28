package com.pgu.palais_divin_back.business.controller;

import com.pgu.palais_divin_back.business.model.Restaurant;
import com.pgu.palais_divin_back.business.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    public Restaurant createRestaurant(@Valid @RequestBody Restaurant restaurant) {
        return restaurantService.createRestaurant(restaurant);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Restaurant> getRestaurantById(@PathVariable String id) {
        return restaurantService.findRestaurantByIdWithRating(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Restaurant> getAllRestaurants() {
        return restaurantService.findAllRestaurantsWithRatings();
    }

    @GetMapping("/search")
    public List<Restaurant> searchRestaurants(@RequestParam String name) {
        return restaurantService.searchRestaurantsByName(name);
    }

    @PostMapping("/{id}/photo")
    public ResponseEntity<Restaurant> addPhoto(
            @PathVariable String id,
            @RequestParam("photo") MultipartFile photoFile) {
        try {
            Restaurant restaurant = restaurantService.addPhotoToRestaurant(id, photoFile);
            return ResponseEntity.ok(restaurant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}/photo")
    public ResponseEntity<Restaurant> removePhoto(@PathVariable String id) {
        try {
            Restaurant restaurant = restaurantService.removePhotoFromRestaurant(id);
            return ResponseEntity.ok(restaurant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

