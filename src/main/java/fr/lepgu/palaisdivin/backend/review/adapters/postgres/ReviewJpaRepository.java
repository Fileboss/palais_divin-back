package fr.lepgu.palaisdivin.backend.review.adapters.postgres;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {}
