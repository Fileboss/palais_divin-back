package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, UUID> {}
