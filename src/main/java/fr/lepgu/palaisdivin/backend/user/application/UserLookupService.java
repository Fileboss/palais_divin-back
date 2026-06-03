package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.LookupUsersUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserLookupService implements LookupUsersUseCase {

  private final UserRepositoryPort users;

  UserLookupService(UserRepositoryPort users) {
    this.users = users;
  }

  @Override
  public Map<UserId, User> findByIds(Collection<UserId> ids) {
    return users.findByIds(ids);
  }
}
