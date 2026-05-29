package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import java.util.Optional;

public interface InvitationRepositoryPort {

  Invitation save(Invitation invitation);

  Optional<Invitation> findById(InvitationId id);

  Optional<Invitation> findByToken(InvitationToken token);
}
