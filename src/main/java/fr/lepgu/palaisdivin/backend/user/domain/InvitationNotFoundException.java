package fr.lepgu.palaisdivin.backend.user.domain;

import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;

public final class InvitationNotFoundException extends RuntimeException {

  public InvitationNotFoundException(InvitationId id) {
    super("Invitation not found: " + id.value());
  }
}
