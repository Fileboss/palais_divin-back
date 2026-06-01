package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public interface CreateConnectionUseCase {

  ConnectionResult connect(String subject, UserId targetId);
}
