package fr.lepgu.palaisdivin.backend.user.domain.ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.user.domain.OrphanSubjectException;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserRepositoryPortTest {

  private static final String SUBJECT = "kc-subject-xyz";

  @Test
  void requireBySubject_returnsId_whenPresent() {
    UserId expected = UserId.newId();
    User user =
        new User(expected, SUBJECT, "u@example.com", "U", Instant.parse("2026-05-01T00:00:00Z"));
    UserRepositoryPort port = fakePort(Optional.of(user));

    assertThat(port.requireBySubject(SUBJECT)).isEqualTo(expected);
  }

  @Test
  void requireBySubject_throwsOrphanSubject_whenAbsent() {
    UserRepositoryPort port = fakePort(Optional.empty());

    assertThatThrownBy(() -> port.requireBySubject(SUBJECT))
        .isInstanceOf(OrphanSubjectException.class)
        .extracting("subject")
        .isEqualTo(SUBJECT);
  }

  private static UserRepositoryPort fakePort(Optional<User> lookup) {
    return new UserRepositoryPort() {
      @Override
      public User save(User user) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<User> findById(UserId id) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<User> findBySubject(String subject) {
        return lookup;
      }
    };
  }
}
