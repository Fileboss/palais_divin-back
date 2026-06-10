package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ConnectionRepositoryPort;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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

  @Override
  public CursorPage<Connection> findBySource(
      UserId sourceUserId, ConnectionCursor cursor, int size) {
    PageRequest pageable = PageRequest.of(0, size);
    Slice<UserConnectionEntity> slice =
        cursor == null
            ? jpa.findFirstPageBySource(sourceUserId.value(), pageable)
            : jpa.findAfterBySource(
                sourceUserId.value(), cursor.createdAt(), cursor.id().value(), pageable);
    return new CursorPage<>(
        slice.getContent().stream().map(UserConnectionPostgresAdapter::toDomain).toList(),
        slice.hasNext());
  }

  @Override
  public Optional<Connection> deleteBySourceAndTarget(UserId sourceUserId, UserId targetUserId) {
    return jpa.findBySourceUserIdAndTargetUserId(sourceUserId.value(), targetUserId.value())
        .map(
            e -> {
              jpa.delete(e);
              return toDomain(e);
            });
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
