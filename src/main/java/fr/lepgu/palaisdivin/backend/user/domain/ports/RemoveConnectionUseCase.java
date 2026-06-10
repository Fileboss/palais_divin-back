package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public interface RemoveConnectionUseCase {
  void remove(String subject, UserId targetId);
}
