package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.config.InvitationProperties;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-29T10:00:00Z");
  private static final Duration TTL = Duration.ofHours(48);

  @Mock private InvitationRepositoryPort repository;

  private InvitationService service;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    InvitationProperties properties = new InvitationProperties(TTL, "http://localhost/register");
    service = new InvitationService(repository, properties, fixedClock);
  }

  @Test
  void issuePersistsInvitationWithGeneratedIdAndTokenAtClockNow() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Invitation issued = service.issue();

    ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
    org.mockito.Mockito.verify(repository).save(captor.capture());
    Invitation persisted = captor.getValue();
    assertThat(persisted.id()).isNotNull();
    assertThat(persisted.id().value()).isNotNull();
    assertThat(persisted.token()).isNotNull();
    assertThat(persisted.token().value()).isNotBlank();
    assertThat(persisted.createdAt()).isEqualTo(FIXED_NOW);
    assertThat(persisted.expiresAt()).isEqualTo(FIXED_NOW.plus(TTL));
    assertThat(persisted.consumedAt()).isNull();
    assertThat(issued).isEqualTo(persisted);
  }

  @Test
  void issueReturnsWhateverRepositoryReturns() {
    Invitation stored =
        new Invitation(
            new fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId(
                java.util.UUID.randomUUID()),
            fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken.newToken(),
            FIXED_NOW.plus(TTL),
            null,
            FIXED_NOW);
    when(repository.save(any())).thenReturn(stored);

    assertThat(service.issue()).isSameAs(stored);
  }

  @Test
  void successiveIssuesProduceDistinctTokensAndIds() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Invitation a = service.issue();
    Invitation b = service.issue();

    assertThat(a.id()).isNotEqualTo(b.id());
    assertThat(a.token()).isNotEqualTo(b.token());
  }
}
