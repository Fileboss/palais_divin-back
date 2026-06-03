package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

  @Mock UserRepositoryPort users;

  @Test
  void findById_returnsUser_whenPresent() {
    FindUserService service = new FindUserService(users);
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "u@example.test", "Alice", NOW);
    when(users.findById(id)).thenReturn(Optional.of(user));

    User got = service.findById(id);

    assertThat(got).isSameAs(user);
  }

  @Test
  void findById_throwsUserNotFound_whenAbsent() {
    FindUserService service = new FindUserService(users);
    UserId id = UserId.newId();
    when(users.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(id))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining(id.value().toString());
  }
}
