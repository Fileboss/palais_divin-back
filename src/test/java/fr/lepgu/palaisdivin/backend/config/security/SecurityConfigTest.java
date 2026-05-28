package fr.lepgu.palaisdivin.backend.config.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void public_path_is_permitted() throws Exception {
    mockMvc.perform(get("/api/v1/public/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void user_path_requires_auth_returns_problem_detail_401() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/anything"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
        .andExpect(content().string(containsString("/problems/unauthorized")));
  }

  @Test
  void admin_path_without_token_returns_problem_detail_401() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/anything"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
        .andExpect(content().string(containsString("/problems/unauthorized")));
  }

  @Test
  void unmapped_path_requires_auth_returns_problem_detail_401() throws Exception {
    mockMvc
        .perform(get("/random"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
        .andExpect(content().string(containsString("/problems/unauthorized")));
  }

  @Test
  void actuator_path_is_permitted() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
  }

  @Test
  void user_path_with_valid_jwt_passes_security() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/user/anything")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void admin_path_with_user_role_returns_problem_detail_403() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/anything")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isForbidden())
        .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
        .andExpect(content().string(containsString("/problems/forbidden")));
  }

  @Test
  void admin_path_with_admin_role_passes_security() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/anything")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isNotFound());
  }
}
