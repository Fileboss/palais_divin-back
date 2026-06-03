package fr.lepgu.palaisdivin.backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

  @Mock UserRepositoryPort users;

  @Test
  void findByIds_delegates_to_repository_and_returns_map() {
    UserLookupService service = new UserLookupService(users);
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "u@example.test", "Alice", NOW);
    Map<UserId, User> expected = Map.of(id, user);
    when(users.findByIds(List.of(id))).thenReturn(expected);

    Map<UserId, User> got = service.findByIds(List.of(id));

    assertThat(got).isSameAs(expected);
    verify(users).findByIds(List.of(id));
  }

  @Test
  void findByIds_empty_input_delegates_to_repository() {
    UserLookupService service = new UserLookupService(users);
    when(users.findByIds(List.of())).thenReturn(Map.of());

    Map<UserId, User> got = service.findByIds(List.of());

    assertThat(got).isEmpty();
    verify(users).findByIds(List.of());
  }
}
