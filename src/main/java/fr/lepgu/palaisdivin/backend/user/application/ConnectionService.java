package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.SelfConnectionException;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.events.ConnectionCreated;
import fr.lepgu.palaisdivin.backend.user.domain.events.ConnectionRemoved;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CheckFollowUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ConnectionRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CreateConnectionUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ListMyConnectionsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RemoveConnectionUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConnectionService
    implements CreateConnectionUseCase,
        ListMyConnectionsUseCase,
        RemoveConnectionUseCase,
        CheckFollowUseCase {

  private static final String AGGREGATE_TYPE = "Connection";

  private final UserRepositoryPort users;
  private final ConnectionRepositoryPort connections;
  private final OutboxPublisher outbox;
  private final Clock clock;

  public ConnectionService(
      UserRepositoryPort users,
      ConnectionRepositoryPort connections,
      OutboxPublisher outbox,
      Clock clock) {
    this.users = users;
    this.connections = connections;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public ConnectionResult connect(String subject, UserId targetId) {
    UserId sourceId = users.requireBySubject(subject);

    if (sourceId.equals(targetId)) {
      throw new SelfConnectionException(sourceId);
    }

    if (users.findById(targetId).isEmpty()) {
      throw new UserNotFoundException(targetId);
    }

    Optional<Connection> existing = connections.findBySourceAndTarget(sourceId, targetId);
    if (existing.isPresent()) {
      return new ConnectionResult(existing.get(), false);
    }

    Connection connection =
        new Connection(ConnectionId.newId(), sourceId, targetId, clock.instant());
    Connection saved = connections.save(connection);

    outbox.publish(
        AGGREGATE_TYPE,
        saved.id().value(),
        "ConnectionCreated",
        new ConnectionCreated(
            saved.id().value(),
            saved.sourceUserId().value(),
            saved.targetUserId().value(),
            saved.createdAt()));

    return new ConnectionResult(saved, true);
  }

  @Override
  @Transactional(readOnly = true)
  public CursorPage<Connection> listMine(String subject, ConnectionCursor cursor, int size) {
    UserId sourceId = users.requireBySubject(subject);
    return connections.findBySource(sourceId, cursor, size);
  }

  @Override
  public void remove(String subject, UserId targetId) {
    UserId sourceId = users.requireBySubject(subject);

    if (sourceId.equals(targetId)) {
      throw new SelfConnectionException(sourceId);
    }

    Optional<Connection> deleted = connections.deleteBySourceAndTarget(sourceId, targetId);
    if (deleted.isEmpty()) {
      return;
    }

    Connection c = deleted.get();
    outbox.publish(
        AGGREGATE_TYPE,
        c.id().value(),
        "ConnectionRemoved",
        new ConnectionRemoved(
            c.id().value(), c.sourceUserId().value(), c.targetUserId().value(), clock.instant()));
  }

  @Override
  @Transactional(readOnly = true)
  public Boolean isFollowedByViewer(String viewerSubject, UserId targetId) {
    return users
        .findBySubject(viewerSubject)
        .map(User::id)
        .filter(viewerId -> !viewerId.equals(targetId))
        .map(viewerId -> connections.existsBy(viewerId, targetId))
        .orElse(null);
  }
}
