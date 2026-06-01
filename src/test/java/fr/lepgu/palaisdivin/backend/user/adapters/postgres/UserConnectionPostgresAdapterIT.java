package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, UserConnectionPostgresAdapter.class})
class UserConnectionPostgresAdapterIT {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-06-01T10:00:00Z");

  private static final UUID SOURCE_UUID = UUID.randomUUID();
  private static final UUID TARGET_UUID = UUID.randomUUID();
  private static final UUID OTHER_UUID = UUID.randomUUID();

  private static final UserId SOURCE_ID = new UserId(SOURCE_UUID);
  private static final UserId TARGET_ID = new UserId(TARGET_UUID);
  private static final UserId OTHER_ID = new UserId(OTHER_UUID);

  @Autowired UserConnectionPostgresAdapter adapter;
  @PersistenceContext EntityManager em;

  @BeforeEach
  void seedUsers() {
    insertUser(SOURCE_UUID, "subj-src", "src@example.com", "Source");
    insertUser(TARGET_UUID, "subj-tgt", "tgt@example.com", "Target");
    insertUser(OTHER_UUID, "subj-other", "other@example.com", "Other");
  }

  private void insertUser(UUID id, String subject, String email, String displayName) {
    em.createNativeQuery(
            "INSERT INTO app_user (id, subject, email, display_name) VALUES (?, ?, ?, ?)")
        .setParameter(1, id)
        .setParameter(2, subject)
        .setParameter(3, email)
        .setParameter(4, displayName)
        .executeUpdate();
  }

  @Test
  void roundTripPreservesAllFields() {
    ConnectionId id = ConnectionId.newId();
    Connection input = new Connection(id, SOURCE_ID, TARGET_ID, FIXED_CREATED_AT);

    Connection saved = adapter.save(input);
    Optional<Connection> found = adapter.findBySourceAndTarget(SOURCE_ID, TARGET_ID);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Connection out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.sourceUserId()).isEqualTo(SOURCE_ID);
    assertThat(out.targetUserId()).isEqualTo(TARGET_ID);
    assertThat(out.createdAt()).isEqualTo(FIXED_CREATED_AT);
  }

  @Test
  void findBySourceAndTargetMissingReturnsEmpty() {
    assertThat(adapter.findBySourceAndTarget(SOURCE_ID, TARGET_ID)).isEmpty();
  }

  @Test
  void uniqueSourceTargetPairIsEnforced() {
    Connection first = new Connection(ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT);
    Connection dup = new Connection(ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT);
    adapter.save(first);

    assertThatThrownBy(
            () -> {
              adapter.save(dup);
              em.flush();
            })
        .hasMessageContaining("uq_user_connection_source_target");
  }

  @Test
  void reverseDirectionIsAllowed() {
    Connection aToB = new Connection(ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT);
    Connection bToA = new Connection(ConnectionId.newId(), TARGET_ID, SOURCE_ID, FIXED_CREATED_AT);

    adapter.save(aToB);
    adapter.save(bToA);

    assertThat(adapter.findBySourceAndTarget(SOURCE_ID, TARGET_ID)).isPresent();
    assertThat(adapter.findBySourceAndTarget(TARGET_ID, SOURCE_ID)).isPresent();
  }
}
