package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
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

  @Test
  void findBySource_returnsMostRecentFirst() {
    Connection older =
        new Connection(
            ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT.minusSeconds(60));
    Connection newer =
        new Connection(ConnectionId.newId(), SOURCE_ID, new UserId(OTHER_UUID), FIXED_CREATED_AT);
    adapter.save(older);
    adapter.save(newer);

    CursorPage<Connection> page = adapter.findBySource(SOURCE_ID, null, 20);

    assertThat(page.hasNext()).isFalse();
    assertThat(page.data()).extracting(Connection::id).containsExactly(newer.id(), older.id());
  }

  @Test
  void findBySource_keysetWalksAcrossPages() {
    UUID[] extraTargets = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
    for (UUID t : extraTargets) {
      insertUser(t, "subj-" + t, t + "@example.com", "Extra " + t);
    }
    // Insert 5 connections with strictly increasing createdAt → newest = index 4.
    List<Connection> inserted =
        List.of(
            new Connection(
                ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT.minusSeconds(40)),
            new Connection(
                ConnectionId.newId(),
                SOURCE_ID,
                new UserId(OTHER_UUID),
                FIXED_CREATED_AT.minusSeconds(30)),
            new Connection(
                ConnectionId.newId(),
                SOURCE_ID,
                new UserId(extraTargets[0]),
                FIXED_CREATED_AT.minusSeconds(20)),
            new Connection(
                ConnectionId.newId(),
                SOURCE_ID,
                new UserId(extraTargets[1]),
                FIXED_CREATED_AT.minusSeconds(10)),
            new Connection(
                ConnectionId.newId(), SOURCE_ID, new UserId(extraTargets[2]), FIXED_CREATED_AT));
    inserted.forEach(adapter::save);

    CursorPage<Connection> p1 = adapter.findBySource(SOURCE_ID, null, 2);
    assertThat(p1.data()).hasSize(2);
    assertThat(p1.hasNext()).isTrue();
    assertThat(p1.data().get(0).createdAt()).isEqualTo(FIXED_CREATED_AT);
    assertThat(p1.data().get(1).createdAt()).isEqualTo(FIXED_CREATED_AT.minusSeconds(10));

    Connection last1 = p1.data().getLast();
    ConnectionCursor cursor1 = new ConnectionCursor(last1.createdAt(), last1.id());
    CursorPage<Connection> p2 = adapter.findBySource(SOURCE_ID, cursor1, 2);
    assertThat(p2.data()).hasSize(2);
    assertThat(p2.hasNext()).isTrue();
    assertThat(p2.data().get(0).createdAt()).isEqualTo(FIXED_CREATED_AT.minusSeconds(20));
    assertThat(p2.data().get(1).createdAt()).isEqualTo(FIXED_CREATED_AT.minusSeconds(30));

    Connection last2 = p2.data().getLast();
    ConnectionCursor cursor2 = new ConnectionCursor(last2.createdAt(), last2.id());
    CursorPage<Connection> p3 = adapter.findBySource(SOURCE_ID, cursor2, 2);
    assertThat(p3.data()).hasSize(1);
    assertThat(p3.hasNext()).isFalse();
    assertThat(p3.data().getFirst().createdAt()).isEqualTo(FIXED_CREATED_AT.minusSeconds(40));
  }

  @Test
  void findBySource_isolatesOtherSources() {
    Connection myFollow =
        new Connection(ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT);
    Connection otherFollow =
        new Connection(ConnectionId.newId(), new UserId(OTHER_UUID), TARGET_ID, FIXED_CREATED_AT);
    adapter.save(myFollow);
    adapter.save(otherFollow);

    CursorPage<Connection> mine = adapter.findBySource(SOURCE_ID, null, 20);

    assertThat(mine.data()).extracting(Connection::id).containsExactly(myFollow.id());
  }

  @Test
  void deleteBySourceAndTarget_returnsDeletedRow_andRemovesFromDb() {
    ConnectionId id = ConnectionId.newId();
    Connection input = new Connection(id, SOURCE_ID, TARGET_ID, FIXED_CREATED_AT);
    adapter.save(input);

    Optional<Connection> deleted = adapter.deleteBySourceAndTarget(SOURCE_ID, TARGET_ID);

    assertThat(deleted).isPresent();
    assertThat(deleted.get().id()).isEqualTo(id);
    assertThat(deleted.get().sourceUserId()).isEqualTo(SOURCE_ID);
    assertThat(deleted.get().targetUserId()).isEqualTo(TARGET_ID);
    assertThat(deleted.get().createdAt()).isEqualTo(FIXED_CREATED_AT);

    em.flush();
    em.clear();
    assertThat(adapter.findBySourceAndTarget(SOURCE_ID, TARGET_ID)).isEmpty();
  }

  @Test
  void deleteBySourceAndTarget_absent_returnsEmptyOptional() {
    Optional<Connection> deleted = adapter.deleteBySourceAndTarget(SOURCE_ID, TARGET_ID);

    assertThat(deleted).isEmpty();
  }

  @Test
  void existsBy_returnsTrue_whenRowPresent() {
    adapter.save(new Connection(ConnectionId.newId(), SOURCE_ID, TARGET_ID, FIXED_CREATED_AT));

    assertThat(adapter.existsBy(SOURCE_ID, TARGET_ID)).isTrue();
  }

  @Test
  void existsBy_returnsFalse_whenRowAbsent() {
    assertThat(adapter.existsBy(SOURCE_ID, TARGET_ID)).isFalse();
  }
}
