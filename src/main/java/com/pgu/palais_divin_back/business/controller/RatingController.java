package com.pgu.palais_divin_back.business.controller;

import com.pgu.palais_divin_back.business.dto.RatingDto;
import com.pgu.palais_divin_back.business.service.RatingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<RatingDto> rateRestaurant(@RequestBody RatingRequest request) {
        try {
            RatingDto rating = ratingService.rateRestaurant(
                    request.getUserId(),
                    request.getRestaurantId(),
                    request.getScore()
            );
            return ResponseEntity.ok(rating);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{userId}/{restaurantId}")
    public ResponseEntity<String> deleteRating(@PathVariable String userId, @PathVariable String restaurantId) {
        try {
            ratingService.deleteRating(userId, restaurantId);
            return ResponseEntity.ok("Rating deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting rating");
        }
    }

    @Data
    public static class RatingRequest {
        private String userId;
        private String restaurantId;
        private Integer score;
    }
}