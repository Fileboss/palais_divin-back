package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Optional;

public interface ConnectionRepositoryPort {

  Connection save(Connection connection);

  Optional<Connection> findBySourceAndTarget(UserId sourceUserId, UserId targetUserId);
}
