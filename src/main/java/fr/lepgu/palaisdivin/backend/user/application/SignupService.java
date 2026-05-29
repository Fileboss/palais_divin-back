package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotUsableException;
import fr.lepgu.palaisdivin.backend.user.domain.events.UserCreated;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.model.KeycloakUserId;
import fr.lepgu.palaisdivin.backend.user.domain.model.NewKeycloakUser;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.KeycloakAdminPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.SignupUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService implements SignupUseCase {

  private static final List<String> DEFAULT_ROLES = List.of("USER");

  private final InvitationRepositoryPort invitations;
  private final UserRepositoryPort users;
  private final KeycloakAdminPort keycloakAdmin;
  private final OutboxPublisher outbox;
  private final Clock clock;

  public SignupService(
      InvitationRepositoryPort invitations,
      UserRepositoryPort users,
      KeycloakAdminPort keycloakAdmin,
      OutboxPublisher outbox,
      Clock clock) {
    this.invitations = invitations;
    this.users = users;
    this.keycloakAdmin = keycloakAdmin;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  @Transactional
  public User signup(String token, String email, String displayName, String password) {
    InvitationToken invitationToken = new InvitationToken(token);
    Invitation invitation =
        invitations
            .findByToken(invitationToken)
            .orElseThrow(() -> new InvitationNotFoundException(invitationToken));
    if (invitation.isExpired(clock)) {
      throw InvitationNotUsableException.expired(invitation.expiresAt());
    }
    if (invitation.isConsumed()) {
      throw InvitationNotUsableException.alreadyConsumed(invitation.consumedAt());
    }

    // Keycloak first — we need its sub as the canonical `subject` in Postgres.
    // If the Postgres tx rolls back after this point, the KC user is orphaned (documented
    // limitation).
    KeycloakUserId kcId =
        keycloakAdmin.createUser(
            new NewKeycloakUser(email, email, displayName, password, DEFAULT_ROLES));

    Instant now = Instant.now(clock);
    User saved = users.save(new User(UserId.newId(), kcId.value(), email, displayName, now));
    invitations.save(invitation.consume(now));
    outbox.publish(
        "User",
        saved.id().value(),
        "UserCreated",
        new UserCreated(
            saved.id().value(),
            saved.subject(),
            saved.email(),
            saved.displayName(),
            saved.createdAt()));
    return saved;
  }
}
