package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findBySubject(String subject);
}
