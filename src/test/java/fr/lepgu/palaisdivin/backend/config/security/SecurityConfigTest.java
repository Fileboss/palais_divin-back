package fr.lepgu.palaisdivin.backend.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired MockMvc mockMvc;

  @Test
  void public_path_is_permitted() throws Exception {
    mockMvc.perform(get("/api/v1/public/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void user_path_requires_auth() throws Exception {
    mockMvc.perform(get("/api/v1/user/anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void admin_path_requires_auth() throws Exception {
    mockMvc.perform(get("/api/v1/admin/anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void unmapped_path_requires_auth() throws Exception {
    mockMvc.perform(get("/random")).andExpect(status().isUnauthorized());
  }

  @Test
  void actuator_path_is_permitted() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
  }
}
