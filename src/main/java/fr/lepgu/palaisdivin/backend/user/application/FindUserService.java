package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.FindUserUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class FindUserService implements FindUserUseCase {

  private final UserRepositoryPort users;

  FindUserService(UserRepositoryPort users) {
    this.users = users;
  }

  @Override
  public User findById(UserId id) {
    return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
  }
}
