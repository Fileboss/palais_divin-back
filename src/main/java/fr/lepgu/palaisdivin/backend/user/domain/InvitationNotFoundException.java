package fr.lepgu.palaisdivin.backend.user.domain;

import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;

public final class InvitationNotFoundException extends RuntimeException {

  public InvitationNotFoundException(InvitationId id) {
    super("Invitation not found: " + id.value());
  }

  public InvitationNotFoundException(InvitationToken token) {
    super("Invitation not found for token: " + token.value());
  }
}
