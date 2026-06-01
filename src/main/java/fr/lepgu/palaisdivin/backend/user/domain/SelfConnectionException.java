package fr.lepgu.palaisdivin.backend.user.domain;

import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public final class SelfConnectionException extends RuntimeException {

  public SelfConnectionException(UserId userId) {
    super("User cannot connect to themselves: " + userId.value());
  }
}
