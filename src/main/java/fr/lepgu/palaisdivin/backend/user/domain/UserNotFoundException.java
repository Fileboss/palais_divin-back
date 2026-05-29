package fr.lepgu.palaisdivin.backend.user.domain;

import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;

public final class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(UserId id) {
    super("User not found: " + id.value());
  }
}
