package fr.lepgu.palaisdivin.backend.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewKeycloakUserTest {

  private static final String USERNAME = "kc-user";
  private static final String EMAIL = "kc-user@example.com";
  private static final String DISPLAY_NAME = "Kc User";
  private static final String PASSWORD = "temp-pass";
  private static final List<String> ROLES = List.of("USER");

  @Test
  void buildsWithValidInputs() {
    NewKeycloakUser u = new NewKeycloakUser(USERNAME, EMAIL, DISPLAY_NAME, PASSWORD, ROLES);

    assertThat(u.username()).isEqualTo(USERNAME);
    assertThat(u.email()).isEqualTo(EMAIL);
    assertThat(u.displayName()).isEqualTo(DISPLAY_NAME);
    assertThat(u.temporaryPassword()).isEqualTo(PASSWORD);
    assertThat(u.realmRoles()).containsExactly("USER");
  }

  @Test
  void rejectsNullUsername() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NewKeycloakUser(null, EMAIL, DISPLAY_NAME, PASSWORD, ROLES))
        .withMessage("username");
  }

  @Test
  void rejectsNullEmail() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NewKeycloakUser(USERNAME, null, DISPLAY_NAME, PASSWORD, ROLES))
        .withMessage("email");
  }

  @Test
  void rejectsNullDisplayName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NewKeycloakUser(USERNAME, EMAIL, null, PASSWORD, ROLES))
        .withMessage("displayName");
  }

  @Test
  void rejectsNullTemporaryPassword() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NewKeycloakUser(USERNAME, EMAIL, DISPLAY_NAME, null, ROLES))
        .withMessage("temporaryPassword");
  }

  @Test
  void rejectsNullRealmRoles() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NewKeycloakUser(USERNAME, EMAIL, DISPLAY_NAME, PASSWORD, null))
        .withMessage("realmRoles");
  }

  @Test
  void rejectsEmptyRealmRoles() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NewKeycloakUser(USERNAME, EMAIL, DISPLAY_NAME, PASSWORD, List.of()))
        .withMessageContaining("must not be empty");
  }

  @Test
  void defensivelyCopiesRealmRoles() {
    List<String> mutable = new ArrayList<>(List.of("USER"));
    NewKeycloakUser u = new NewKeycloakUser(USERNAME, EMAIL, DISPLAY_NAME, PASSWORD, mutable);

    mutable.add("ADMIN");

    assertThat(u.realmRoles()).containsExactly("USER");
  }
}
