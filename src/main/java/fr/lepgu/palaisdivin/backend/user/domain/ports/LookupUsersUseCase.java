package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Collection;
import java.util.Map;

public interface LookupUsersUseCase {

  Map<UserId, User> findByIds(Collection<UserId> ids);
}
