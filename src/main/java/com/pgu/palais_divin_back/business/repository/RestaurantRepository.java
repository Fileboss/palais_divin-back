package com.pgu.palais_divin_back.business.repository;

import com.pgu.palais_divin_back.business.model.Restaurant;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RestaurantRepository extends Neo4jRepository<Restaurant, UUID> {

    // Recherche par nom
    @Query("MATCH (r:Restaurant) WHERE toLower(r.name) CONTAINS toLower($name) RETURN r")
    List<Restaurant> findByNameContainingIgnoreCase(@Param("name") String name);

    // Mettre à jour la moyenne d'un restaurant spécifique
    @Query("MATCH (r:Restaurant {uuid: $restaurantUuid})  " +
            "OPTIONAL MATCH (r)<-[rating:RATED]-(u:User) " +
            "WITH r, AVG(rating.score) as avgRating, COUNT(rating) as ratingCount " +
            "SET r.averageRating = avgRating, r.ratingCount = ratingCount")
    void updateRestaurantRating(@Param("restaurantUuid") String restaurantUuid);

    // Mettre à jour toutes les moyennes
    @Query("MATCH (r:Restaurant) " +
            "OPTIONAL MATCH (r)<-[rating:RATED]-(u:User) " +
            "WITH r, AVG(rating.score) as avgRating, COUNT(rating) as ratingCount " +
            "SET r.averageRating = avgRating, r.ratingCount = ratingCount")
    void updateAllRestaurantRatings();
}