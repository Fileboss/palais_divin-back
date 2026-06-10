package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CheckFollowUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.FindUserUseCase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PublicUserRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicUserRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");
  private static final String VIEWER_SUBJECT = "kc-viewer-xyz";

  @Autowired MockMvc mockMvc;

  @MockitoBean FindUserUseCase findUser;
  @MockitoBean CheckFollowUseCase checkFollow;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor viewerJwt() {
    return jwt()
        .jwt(j -> j.subject(VIEWER_SUBJECT))
        .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void get_anonymous_returnsUserWithFollowedNull() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.displayName").value("Alice"))
        .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT.toString()))
        .andExpect(jsonPath("$.isFollowedByMe").value(org.hamcrest.Matchers.nullValue()));

    verifyNoInteractions(checkFollow);
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

  @Test
  void get_authenticatedViewerFollowingTarget_returnsTrue() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);
    when(checkFollow.isFollowedByViewer(eq(VIEWER_SUBJECT), eq(id))).thenReturn(Boolean.TRUE);

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()).with(viewerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isFollowedByMe").value(true));

    verify(checkFollow).isFollowedByViewer(VIEWER_SUBJECT, id);
  }

  @Test
  void get_authenticatedViewerNotFollowingTarget_returnsFalse() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "bob@example.test", "Bob", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);
    when(checkFollow.isFollowedByViewer(eq(VIEWER_SUBJECT), eq(id))).thenReturn(Boolean.FALSE);

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()).with(viewerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isFollowedByMe").value(false));
  }

  @Test
  void get_authenticatedSelfLookup_returnsFollowedNull() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "self@example.test", "Self", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);
    when(checkFollow.isFollowedByViewer(eq(VIEWER_SUBJECT), eq(id))).thenReturn(null);

    mockMvc
        .perform(get("/api/v1/public/users/{userId}", id.value()).with(viewerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isFollowedByMe").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void get_anonymousNeverConsultsCheckFollow() throws Exception {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);
    when(findUser.findById(id)).thenReturn(user);

    mockMvc.perform(get("/api/v1/public/users/{userId}", id.value())).andExpect(status().isOk());

    verify(checkFollow, org.mockito.Mockito.never()).isFollowedByViewer(any(), any());
  }
}
