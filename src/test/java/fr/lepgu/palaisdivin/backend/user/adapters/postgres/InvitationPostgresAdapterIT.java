package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, InvitationPostgresAdapter.class})
class InvitationPostgresAdapterIT {

  private static final Instant CREATED_AT = Instant.parse("2026-05-29T10:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(48L * 3600L);

  @Autowired InvitationPostgresAdapter adapter;

  @Test
  void roundTripPreservesAllFieldsIncludingNullConsumedAt() {
    InvitationId id = InvitationId.newId();
    InvitationToken token = InvitationToken.newToken();
    Invitation input = new Invitation(id, token, EXPIRES_AT, null, CREATED_AT);

    Invitation saved = adapter.save(input);
    Optional<Invitation> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Invitation out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.token()).isEqualTo(token);
    assertThat(out.expiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(out.consumedAt()).isNull();
    assertThat(out.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void roundTripPreservesNonNullConsumedAt() {
    InvitationId id = InvitationId.newId();
    InvitationToken token = InvitationToken.newToken();
    Instant consumedAt = CREATED_AT.plusSeconds(120);
    Invitation input = new Invitation(id, token, EXPIRES_AT, consumedAt, CREATED_AT);

    adapter.save(input);

    assertThat(adapter.findById(id))
        .isPresent()
        .hasValueSatisfying(i -> assertThat(i.consumedAt()).isEqualTo(consumedAt));
  }

  @Test
  void findByTokenReturnsSavedInvitation() {
    InvitationId id = InvitationId.newId();
    InvitationToken token = InvitationToken.newToken();
    Invitation input = new Invitation(id, token, EXPIRES_AT, null, CREATED_AT);

    adapter.save(input);

    assertThat(adapter.findByToken(token))
        .isPresent()
        .hasValueSatisfying(i -> assertThat(i.id()).isEqualTo(id));
  }

  @Test
  void findByTokenReturnsEmptyForMissingToken() {
    assertThat(adapter.findByToken(InvitationToken.newToken())).isEmpty();
  }

  @Test
  void reSavingWithSameIdUpdatesExistingRow() {
    InvitationId id = InvitationId.newId();
    InvitationToken token = InvitationToken.newToken();
    Invitation pristine = new Invitation(id, token, EXPIRES_AT, null, CREATED_AT);
    adapter.save(pristine);

    Instant consumedAt = CREATED_AT.plusSeconds(300);
    Invitation consumed = new Invitation(id, token, EXPIRES_AT, consumedAt, CREATED_AT);
    adapter.save(consumed);

    assertThat(adapter.findById(id))
        .isPresent()
        .hasValueSatisfying(i -> assertThat(i.consumedAt()).isEqualTo(consumedAt));
  }
}
