package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Optional;

public interface UserRepositoryPort {

  User save(User user);

  Optional<User> findById(UserId id);

  Optional<User> findBySubject(String subject);
}
