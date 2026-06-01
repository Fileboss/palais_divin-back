package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserConnectionJpaRepository extends JpaRepository<UserConnectionEntity, UUID> {

  Optional<UserConnectionEntity> findBySourceUserIdAndTargetUserId(
      UUID sourceUserId, UUID targetUserId);
}
