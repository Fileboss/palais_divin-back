package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public interface CheckFollowUseCase {

  Boolean isFollowedByViewer(String viewerSubject, UserId targetId);
}
