package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;

public interface IssueInvitationUseCase {

  Invitation issue();
}
