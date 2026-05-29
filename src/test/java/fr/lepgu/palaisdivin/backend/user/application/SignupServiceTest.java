package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotUsableException;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotUsableException.Reason;
import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import fr.lepgu.palaisdivin.backend.user.domain.events.UserCreated;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.model.KeycloakUserId;
import fr.lepgu.palaisdivin.backend.user.domain.model.NewKeycloakUser;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.KeycloakAdminPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-29T10:00:00Z");
  private static final Instant CREATED_AT = FIXED_NOW.minusSeconds(3600);
  private static final Instant EXPIRES_AT = FIXED_NOW.plusSeconds(3600);
  private static final String TOKEN = "abc-token";
  private static final String EMAIL = "new@example.test";
  private static final String DISPLAY_NAME = "New User";
  private static final String PASSWORD = "P4ssw0rd!";

  @Mock private InvitationRepositoryPort invitations;
  @Mock private UserRepositoryPort users;
  @Mock private KeycloakAdminPort keycloakAdmin;
  @Mock private OutboxPublisher outbox;

  private SignupService service;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    service = new SignupService(invitations, users, keycloakAdmin, outbox, fixedClock);
  }

  @Test
  void happyPath_createsKeycloakUser_persistsUser_consumesInvitation_publishesOutbox() {
    Invitation pending =
        new Invitation(
            InvitationId.newId(), new InvitationToken(TOKEN), EXPIRES_AT, null, CREATED_AT);
    when(invitations.findByToken(new InvitationToken(TOKEN))).thenReturn(Optional.of(pending));
    UUID kcSub = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    when(keycloakAdmin.createUser(any(NewKeycloakUser.class)))
        .thenReturn(new KeycloakUserId(kcSub.toString()));
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = service.signup(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD);

    ArgumentCaptor<NewKeycloakUser> kcCaptor = ArgumentCaptor.forClass(NewKeycloakUser.class);
    verify(keycloakAdmin).createUser(kcCaptor.capture());
    NewKeycloakUser submitted = kcCaptor.getValue();
    assertThat(submitted.username()).isEqualTo(EMAIL);
    assertThat(submitted.email()).isEqualTo(EMAIL);
    assertThat(submitted.displayName()).isEqualTo(DISPLAY_NAME);
    assertThat(submitted.temporaryPassword()).isEqualTo(PASSWORD);
    assertThat(submitted.realmRoles()).isEqualTo(List.of("USER"));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(users).save(userCaptor.capture());
    User persisted = userCaptor.getValue();
    assertThat(persisted.subject()).isEqualTo(kcSub.toString());
    assertThat(persisted.email()).isEqualTo(EMAIL);
    assertThat(persisted.displayName()).isEqualTo(DISPLAY_NAME);
    assertThat(persisted.createdAt()).isEqualTo(FIXED_NOW);
    assertThat(result).isEqualTo(persisted);

    ArgumentCaptor<Invitation> invCaptor = ArgumentCaptor.forClass(Invitation.class);
    verify(invitations).save(invCaptor.capture());
    assertThat(invCaptor.getValue().consumedAt()).isEqualTo(FIXED_NOW);
    assertThat(invCaptor.getValue().id()).isEqualTo(pending.id());

    ArgumentCaptor<UserCreated> eventCaptor = ArgumentCaptor.forClass(UserCreated.class);
    verify(outbox)
        .publish(eq("User"), eq(persisted.id().value()), eq("UserCreated"), eventCaptor.capture());
    UserCreated event = eventCaptor.getValue();
    assertThat(event.id()).isEqualTo(persisted.id().value());
    assertThat(event.subject()).isEqualTo(kcSub.toString());
    assertThat(event.email()).isEqualTo(EMAIL);
    assertThat(event.displayName()).isEqualTo(DISPLAY_NAME);
    assertThat(event.createdAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void unknownToken_throwsNotFound_noKeycloakOrPersistenceWrites() {
    when(invitations.findByToken(new InvitationToken(TOKEN))).thenReturn(Optional.empty());

    assertThatExceptionOfType(InvitationNotFoundException.class)
        .isThrownBy(() -> service.signup(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD));

    verifyNoInteractions(keycloakAdmin, users, outbox);
    verify(invitations, never()).save(any());
  }

  @Test
  void expiredToken_throwsNotUsableWithExpiredReason_noKeycloakOrPersistenceWrites() {
    Instant expired = FIXED_NOW.minusSeconds(1);
    Invitation expiredInv =
        new Invitation(
            InvitationId.newId(),
            new InvitationToken(TOKEN),
            expired,
            null,
            expired.minusSeconds(60));
    when(invitations.findByToken(new InvitationToken(TOKEN))).thenReturn(Optional.of(expiredInv));

    assertThatThrownBy(() -> service.signup(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))
        .isInstanceOfSatisfying(
            InvitationNotUsableException.class,
            ex -> assertThat(ex.reason()).isEqualTo(Reason.EXPIRED));

    verifyNoInteractions(keycloakAdmin, users, outbox);
    verify(invitations, never()).save(any());
  }

  @Test
  void alreadyConsumedToken_throwsNotUsableWithConsumedReason_noKeycloakOrPersistenceWrites() {
    Invitation consumed =
        new Invitation(
            InvitationId.newId(),
            new InvitationToken(TOKEN),
            EXPIRES_AT,
            FIXED_NOW.minusSeconds(60),
            CREATED_AT);
    when(invitations.findByToken(new InvitationToken(TOKEN))).thenReturn(Optional.of(consumed));

    assertThatThrownBy(() -> service.signup(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))
        .isInstanceOfSatisfying(
            InvitationNotUsableException.class,
            ex -> assertThat(ex.reason()).isEqualTo(Reason.ALREADY_CONSUMED));

    verifyNoInteractions(keycloakAdmin, users, outbox);
    verify(invitations, never()).save(any());
  }

  @Test
  void keycloakFailure_propagates_noPostgresWrites() {
    Invitation pending =
        new Invitation(
            InvitationId.newId(), new InvitationToken(TOKEN), EXPIRES_AT, null, CREATED_AT);
    when(invitations.findByToken(new InvitationToken(TOKEN))).thenReturn(Optional.of(pending));
    when(keycloakAdmin.createUser(any(NewKeycloakUser.class)))
        .thenThrow(new KeycloakOperationException("boom"));

    assertThatExceptionOfType(KeycloakOperationException.class)
        .isThrownBy(() -> service.signup(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD));

    verify(users, never()).save(any());
    verify(invitations, never()).save(any());
    verifyNoInteractions(outbox);
  }
}
