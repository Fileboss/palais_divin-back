package com.pgu.palais_divin_back.business.repository;

import com.pgu.palais_divin_back.business.dto.RestaurantSummaryDto;
import com.pgu.palais_divin_back.business.model.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends Neo4jRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("""
            MATCH (u:User) WHERE u.email = $email
            OPTIONAL MATCH (u)-[r:RATED]->(restaurant:Restaurant)
            RETURN restaurant.name as name, restaurant.address as address, restaurant.uuid as uuid
            """)
    List<RestaurantSummaryDto> findRatedRestaurantsByEmail(@Param("email") String email);

}
