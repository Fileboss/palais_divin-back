package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPostgresAdapter.class})
class UserPostgresAdapterIT {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-29T10:15:30Z");

  @Autowired UserPostgresAdapter adapter;

  @Test
  void roundTripPreservesAllFields() {
    UserId id = UserId.newId();
    String subject = "kc-" + UUID.randomUUID();
    String email = id.value() + "@example.com";
    User input = new User(id, subject, email, "Alice", FIXED_CREATED_AT);

    User saved = adapter.save(input);
    Optional<User> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    User out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.subject()).isEqualTo(subject);
    assertThat(out.email()).isEqualTo(email);
    assertThat(out.displayName()).isEqualTo("Alice");
    assertThat(out.createdAt()).isEqualTo(FIXED_CREATED_AT);
  }

  @Test
  void findBySubjectReturnsSavedUser() {
    UserId id = UserId.newId();
    String subject = "kc-" + UUID.randomUUID();
    String email = id.value() + "@example.com";
    User input = new User(id, subject, email, "Bob", FIXED_CREATED_AT);

    adapter.save(input);

    assertThat(adapter.findBySubject(subject))
        .isPresent()
        .hasValueSatisfying(u -> assertThat(u.id()).isEqualTo(id));
  }

  @Test
  void findByIdReturnsEmptyForMissingId() {
    assertThat(adapter.findById(UserId.newId())).isEmpty();
  }

  @Test
  void findBySubjectReturnsEmptyForMissingSubject() {
    assertThat(adapter.findBySubject("kc-missing-" + UUID.randomUUID())).isEmpty();
  }

  @Test
  void findByIds_returnsMapKeyedByUserId() {
    User alice = saveUser("Alice");
    User bob = saveUser("Bob");

    Map<UserId, User> got = adapter.findByIds(List.of(alice.id(), bob.id()));

    assertThat(got).hasSize(2);
    assertThat(got.get(alice.id()).displayName()).isEqualTo("Alice");
    assertThat(got.get(bob.id()).displayName()).isEqualTo("Bob");
  }

  @Test
  void findByIds_emptyInput_returnsEmptyMap() {
    assertThat(adapter.findByIds(List.of())).isEmpty();
  }

  @Test
  void findByIds_unknownId_silentlySkipped() {
    User alice = saveUser("Alice");
    UserId unknown = UserId.newId();

    Map<UserId, User> got = adapter.findByIds(List.of(alice.id(), unknown));

    assertThat(got).hasSize(1);
    assertThat(got).containsKey(alice.id());
    assertThat(got).doesNotContainKey(unknown);
  }

  private User saveUser(String displayName) {
    UserId id = UserId.newId();
    return adapter.save(
        new User(
            id,
            "kc-" + UUID.randomUUID(),
            id.value() + "@example.test",
            displayName,
            FIXED_CREATED_AT));
  }
}
