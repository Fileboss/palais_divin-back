package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.InvitationProperties;
import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.ports.IssueInvitationUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InvitationRestController.class)
@Import({
  SecurityConfig.class,
  GlobalExceptionHandler.class,
  InvitationRestControllerTest.PropertiesConfig.class
})
class InvitationRestControllerTest {

  private static final String SIGNUP_BASE_URL = "http://frontend.test/register";
  private static final Instant FIXED_EXPIRES_AT = Instant.parse("2026-05-31T10:00:00Z");
  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-29T10:00:00Z");

  @Autowired MockMvc mockMvc;
  @MockitoBean IssueInvitationUseCase issueInvitation;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor adminJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static RequestPostProcessor userJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void post_asAdmin_returns_201_with_location_and_body() throws Exception {
    InvitationId id = new InvitationId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    InvitationToken token = new InvitationToken("test-token-xyz");
    Invitation issued = new Invitation(id, token, FIXED_EXPIRES_AT, null, FIXED_CREATED_AT);
    when(issueInvitation.issue()).thenReturn(issued);

    mockMvc
        .perform(post("/api/v1/admin/invitations").with(adminJwt()))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string("Location", Matchers.endsWith("/api/v1/admin/invitations/" + id.value())))
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.expiresAt").value(FIXED_EXPIRES_AT.toString()))
        .andExpect(jsonPath("$.signupUrl").value(SIGNUP_BASE_URL + "?token=test-token-xyz"));
  }

  @Test
  void post_asUser_returns_403_problem_detail() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/invitations").with(userJwt()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/forbidden"));
  }

  @Test
  void post_anonymous_returns_401_problem_detail() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/invitations"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void get_asAdmin_returns_405_method_not_allowed() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/invitations").with(adminJwt()))
        .andExpect(status().isMethodNotAllowed());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PropertiesConfig {

    @Bean
    InvitationProperties invitationProperties() {
      return new InvitationProperties(Duration.ofHours(48), SIGNUP_BASE_URL);
    }
  }
}
