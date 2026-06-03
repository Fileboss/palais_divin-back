package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.FindUserUseCase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicUserRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicUserRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean FindUserUseCase findUser;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void get_returnsUser_whenFound() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.displayName").value("Alice"))
        .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT.toString()));
  }

  @Test
  void get_omitsSubjectAndEmail() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-secret-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  void get_returns404_whenUnknown() throws Exception {
    UserId id = UserId.newId();
    when(findUser.findById(id)).thenThrow(new UserNotFoundException(id));

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void get_returns400_whenMalformedUuid() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/users/{userId}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400));
  }
}
