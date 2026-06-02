package fr.lepgu.palaisdivin.backend.photo.adapters.postgres;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PhotoJpaRepository extends JpaRepository<PhotoEntity, UUID> {}
