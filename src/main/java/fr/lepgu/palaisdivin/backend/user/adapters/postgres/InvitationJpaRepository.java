package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvitationJpaRepository extends JpaRepository<InvitationEntity, UUID> {

  Optional<InvitationEntity> findByToken(String token);
}
