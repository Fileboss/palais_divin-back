package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

  @Override
  public Map<UserId, User> findByIds(Collection<UserId> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UUID> raw = ids.stream().map(UserId::value).toList();
    return jpa.findAllById(raw).stream()
        .map(UserPostgresAdapter::toDomain)
        .collect(Collectors.toUnmodifiableMap(User::id, u -> u));
  }

  private static UserEntity toEntity(User u) {
    return new UserEntity(u.id().value(), u.subject(), u.email(), u.displayName(), u.createdAt());
  }

  private static User toDomain(UserEntity e) {
    return new User(
        new UserId(e.getId()), e.getSubject(), e.getEmail(), e.getDisplayName(), e.getCreatedAt());
  }
}
