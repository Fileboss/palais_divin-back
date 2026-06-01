package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.user.domain.SelfConnectionException;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.events.ConnectionCreated;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ConnectionRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final String SUBJECT = "kc-subject-abc";

  @Mock UserRepositoryPort users;
  @Mock ConnectionRepositoryPort connections;
  @Mock OutboxPublisher outbox;

  ConnectionService service;

  UserId sourceId;
  UserId targetId;
  User sourceUser;
  User targetUser;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new ConnectionService(users, connections, outbox, clock);

    sourceId = UserId.newId();
    targetId = UserId.newId();
    sourceUser = new User(sourceId, SUBJECT, "source@example.com", "Source", NOW.minusSeconds(60));
    targetUser =
        new User(targetId, "kc-target", "target@example.com", "Target", NOW.minusSeconds(60));
  }

  @Test
  void connectPersistsConnectionAndPublishesEvent() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(sourceUser));
    when(users.findById(targetId)).thenReturn(Optional.of(targetUser));
    when(connections.findBySourceAndTarget(sourceId, targetId)).thenReturn(Optional.empty());
    when(connections.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));

    ConnectionResult result = service.connect(SUBJECT, targetId);

    assertThat(result.created()).isTrue();
    assertThat(result.connection().sourceUserId()).isEqualTo(sourceId);
    assertThat(result.connection().targetUserId()).isEqualTo(targetId);
    assertThat(result.connection().createdAt()).isEqualTo(NOW);

    ArgumentCaptor<ConnectionCreated> eventCaptor =
        ArgumentCaptor.forClass(ConnectionCreated.class);
    verify(outbox)
        .publish(
            eq("Connection"),
            eq(result.connection().id().value()),
            eq("ConnectionCreated"),
            eventCaptor.capture());
    ConnectionCreated event = eventCaptor.getValue();
    assertThat(event.sourceUserId()).isEqualTo(sourceId.value());
    assertThat(event.targetUserId()).isEqualTo(targetId.value());
    assertThat(event.createdAt()).isEqualTo(NOW);
  }

  @Test
  void connectIdempotentReturnsExistingWithoutPublishing() {
    ConnectionId existingId = ConnectionId.newId();
    Connection existing = new Connection(existingId, sourceId, targetId, NOW.minusSeconds(3600));
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(sourceUser));
    when(users.findById(targetId)).thenReturn(Optional.of(targetUser));
    when(connections.findBySourceAndTarget(sourceId, targetId)).thenReturn(Optional.of(existing));

    ConnectionResult result = service.connect(SUBJECT, targetId);

    assertThat(result.created()).isFalse();
    assertThat(result.connection()).isEqualTo(existing);
    verify(connections, never()).save(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void connectThrowsSelfConnectionException() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(sourceUser));

    assertThatThrownBy(() -> service.connect(SUBJECT, sourceId))
        .isInstanceOf(SelfConnectionException.class);

    verify(connections, never()).findBySourceAndTarget(any(), any());
    verify(connections, never()).save(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void connectThrowsUserNotFoundWhenTargetMissing() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.of(sourceUser));
    when(users.findById(targetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.connect(SUBJECT, targetId))
        .isInstanceOf(UserNotFoundException.class);

    verify(connections, never()).save(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void connectThrowsWhenSubjectHasNoAppUser() {
    when(users.findBySubject(SUBJECT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.connect(SUBJECT, targetId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SUBJECT);

    verify(connections, never()).save(any());
    verifyNoInteractions(outbox);
  }
}
