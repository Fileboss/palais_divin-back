package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.config.InvitationProperties;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.IssueInvitationUseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService implements IssueInvitationUseCase {

  private final InvitationRepositoryPort repository;
  private final InvitationProperties properties;
  private final Clock clock;

  public InvitationService(
      InvitationRepositoryPort repository, InvitationProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Invitation issue() {
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(properties.ttl());
    Invitation invitation =
        new Invitation(InvitationId.newId(), InvitationToken.newToken(), expiresAt, null, now);
    return repository.save(invitation);
  }
}
