package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserPostgresAdapter implements UserRepositoryPort {

  private final UserJpaRepository jpa;

  UserPostgresAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public User save(User user) {
    return toDomain(jpa.save(toEntity(user)));
  }

  @Override
  public Optional<User> findById(UserId id) {
    return jpa.findById(id.value()).map(UserPostgresAdapter::toDomain);
  }

  @Override
  public Optional<User> findBySubject(String subject) {
    return jpa.findBySubject(subject).map(UserPostgresAdapter::toDomain);
  }

  private static UserEntity toEntity(User u) {
    return new UserEntity(u.id().value(), u.subject(), u.email(), u.displayName(), u.createdAt());
  }

  private static User toDomain(UserEntity e) {
    return new User(
        new UserId(e.getId()), e.getSubject(), e.getEmail(), e.getDisplayName(), e.getCreatedAt());
  }
}
