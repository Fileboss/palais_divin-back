package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ConnectionRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserConnectionPostgresAdapter implements ConnectionRepositoryPort {

  private final UserConnectionJpaRepository jpa;

  UserConnectionPostgresAdapter(UserConnectionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Connection save(Connection connection) {
    return toDomain(jpa.save(toEntity(connection)));
  }

  @Override
  public Optional<Connection> findBySourceAndTarget(UserId sourceUserId, UserId targetUserId) {
    return jpa.findBySourceUserIdAndTargetUserId(sourceUserId.value(), targetUserId.value())
        .map(UserConnectionPostgresAdapter::toDomain);
  }

  private static UserConnectionEntity toEntity(Connection c) {
    return new UserConnectionEntity(
        c.id().value(), c.sourceUserId().value(), c.targetUserId().value(), c.createdAt());
  }

  private static Connection toDomain(UserConnectionEntity e) {
    return new Connection(
        new ConnectionId(e.getId()),
        new UserId(e.getSourceUserId()),
        new UserId(e.getTargetUserId()),
        e.getCreatedAt());
  }
}
