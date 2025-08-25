package com.pgu.palais_divin_back.business.repository;

import com.pgu.palais_divin_back.business.dto.RatingDto;
import com.pgu.palais_divin_back.business.dto.RestaurantSummaryDto;
import com.pgu.palais_divin_back.business.dto.UserSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RatingRepository {

    public static final String USER_UUID = "userUuid";
    public static final String RESTAURANT_UUID = "restaurantUuid";
    public static final String SCORE = "score";
    private final Neo4jClient neo4jClient;

    public RatingDto createOrUpdateRating(String userUuid, String restaurantUuid, Integer score) {
        return neo4jClient.query("""
            MATCH (u:User {uuid: $userUuid})
            MATCH (r:Restaurant {uuid: $restaurantUuid})
            MERGE (u)-[rel:RATED]->(r)
            ON CREATE SET rel.createdAt = datetime()
            SET rel.score = $score, rel.updatedAt = datetime()
            RETURN
                elementId(rel) AS id,
                rel.score AS score,
                rel.createdAt AS createdAt,
                rel.updatedAt AS updatedAt,
                r { .name, .uuid } AS restaurant,
                u { .firstName, .lastName, .email, .uuid} AS user
        """)
                .bindAll(Map.of(USER_UUID, userUuid, RESTAURANT_UUID, restaurantUuid, SCORE, score))
                .fetchAs(RatingDto.class)
                .mappedBy((typeSystem, rec) -> {
                    var dto = new RatingDto();
                    dto.setId(rec.get("id").asString());
                    dto.setScore(rec.get(SCORE).asInt());
                    dto.setCreatedAt(rec.get("createdAt").asOffsetDateTime());
                    dto.setUpdatedAt(rec.get("updatedAt").asOffsetDateTime());
                    var restaurant = rec.get("restaurant");
                    dto.setRestaurant(new RestaurantSummaryDto(
                            UUID.fromString(restaurant.get("uuid").asString()),
                            restaurant.get("name").asString()
                    ));
                    var user = rec.get("user");
                    dto.setUser(new UserSummaryDto(
                            UUID.fromString(user.get("uuid").asString()),
                            user.get("firstName").asString(),
                            user.get("lastName").asString(),
                            user.get("email").asString()
                    ));
                    return dto;
                })
                .one()
                .orElseThrow();
    }

    public Optional<RatingDto> findByUserAndRestaurant(String userUuid, String restaurantUuid) {
        return neo4jClient.query("""
            MATCH (u:User)-[rel:RATED]->(r:Restaurant)
            WHERE elementId(u) = $userUuid AND elementId(r) = $restaurantUuid
            RETURN
                elementId(rel) AS id,
                rel.score AS score,
                rel.createdAt AS createdAt,
                rel.updatedAt AS updatedAt,
                r { .name, .address, .uuid } AS restaurant,
                u { .firstName, .lastName, .email, .uuid} AS user
        """)
                .bindAll(Map.of(USER_UUID, userUuid, RESTAURANT_UUID, restaurantUuid))
                .fetchAs(RatingDto.class)
                .mappedBy((typeSystem, rec) -> {
                    var dto = new RatingDto();
                    dto.setId(rec.get("id").asString());
                    dto.setScore(rec.get(SCORE).asInt());
                    dto.setCreatedAt(rec.get("createdAt").asOffsetDateTime());
                    dto.setUpdatedAt(rec.get("updatedAt").asOffsetDateTime());
                    var restaurant = rec.get("restaurant");
                    dto.setRestaurant(new RestaurantSummaryDto(
                            UUID.fromString(restaurant.get("uuid").asString()),
                            restaurant.get("name").asString()));
                    var user = rec.get("user");
                    dto.setUser(new UserSummaryDto(
                            UUID.fromString(user.get("uuid").asString()),
                            user.get("firstName").asString(),
                            user.get("lastName").asString(),
                            user.get("email").asString()
                    ));
                    return dto;
                })
                .one();
    }

    public void delete(String userUuid, String restaurantUuid) {
        neo4jClient.query("MATCH (u:User {uuid: $userUuid})-[rating:RATED]->(r:Restaurant {uuid: $restaurantUuid}) " +
                        "DELETE rating")
                .bind(userUuid).to(USER_UUID)
                .bind(restaurantUuid).to(RESTAURANT_UUID)
                .run();
    }
}
