package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotUsableException;
import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.SignupUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SignupRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SignupRestControllerTest {

  private static final String TOKEN = "token-abc-123";
  private static final String EMAIL = "new@example.test";
  private static final String DISPLAY_NAME = "New User";
  private static final String PASSWORD = "P4ssw0rd!";
  private static final Instant CREATED_AT = Instant.parse("2026-05-29T10:00:00Z");

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  @Autowired MockMvc mockMvc;
  @MockitoBean SignupUseCase signupUseCase;
  @MockitoBean JwtDecoder jwtDecoder;

  private String body(SignupRequest r) throws Exception {
    return MAPPER.writeValueAsString(r);
  }

  @Test
  void post_validBody_returns201_withResponseShape() throws Exception {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    User created = new User(new UserId(userId), "subject-uuid", EMAIL, DISPLAY_NAME, CREATED_AT);
    when(signupUseCase.signup(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD)).thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.displayName").value(DISPLAY_NAME))
        .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
        .andExpect(jsonPath("$.subject").doesNotExist())
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void post_missingToken_returns400_validationProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest("", EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"));
  }

  @Test
  void post_invalidEmail_returns400_validationProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, "not-an-email", DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"));
  }

  @Test
  void post_missingDisplayName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, "", PASSWORD))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void post_missingPassword_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, ""))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void post_unknownToken_returns404_notFoundProblemDetail() throws Exception {
    when(signupUseCase.signup(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()))
        .thenThrow(new InvitationNotFoundException(new InvitationToken(TOKEN)));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void post_expiredToken_returns410_invitationNotUsable_reasonExpired() throws Exception {
    when(signupUseCase.signup(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()))
        .thenThrow(InvitationNotUsableException.expired(Instant.parse("2026-05-28T10:00:00Z")));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isGone())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invitation-not-usable"))
        .andExpect(jsonPath("$.reason").value("EXPIRED"));
  }

  @Test
  void post_alreadyConsumedToken_returns410_invitationNotUsable_reasonConsumed() throws Exception {
    when(signupUseCase.signup(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()))
        .thenThrow(
            InvitationNotUsableException.alreadyConsumed(Instant.parse("2026-05-28T10:00:00Z")));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isGone())
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invitation-not-usable"))
        .andExpect(jsonPath("$.reason").value("ALREADY_CONSUMED"));
  }

  @Test
  void post_dataIntegrityViolation_returns409_conflict() throws Exception {
    when(signupUseCase.signup(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()))
        .thenThrow(new DataIntegrityViolationException("unique violation on email"));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/conflict"));
  }

  @Test
  void post_keycloakConflict_returns409_conflict() throws Exception {
    when(signupUseCase.signup(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()))
        .thenThrow(
            new KeycloakOperationException(
                "Keycloak createUser failed with status 409", 409, new RuntimeException()));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/conflict"));
  }

  @Test
  void post_keycloakTransportFailure_returns502_upstreamFailure() throws Exception {
    when(signupUseCase.signup(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()))
        .thenThrow(new KeycloakOperationException("transport failure"));

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(new SignupRequest(TOKEN, EMAIL, DISPLAY_NAME, PASSWORD))))
        .andExpect(status().isBadGateway())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/upstream-failure"));
  }

  @Test
  void get_returns405_methodNotAllowed() throws Exception {
    mockMvc.perform(get("/api/v1/public/signup")).andExpect(status().isMethodNotAllowed());
  }
}
