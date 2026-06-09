package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.tag.domain.TagImplicationNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagImplicationUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagImplicationUseCase;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
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

@WebMvcTest(AdminTagImplicationRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminTagImplicationRestControllerTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateTagImplicationUseCase createImplication;
  @MockitoBean DeleteTagImplicationUseCase deleteImplication;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor adminJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static RequestPostProcessor userJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void post_returns_201_with_location_and_body() throws Exception {
    UUID src = UUID.randomUUID();
    UUID dst = UUID.randomUUID();
    when(createImplication.create(new TagId(src), new TagId(dst)))
        .thenReturn(new TagImplication(new TagId(src), new TagId(dst), NOW));

    mockMvc
        .perform(
            post("/api/v1/admin/tag-implications")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "tagId": "%s", "impliesTagId": "%s" }
                    """
                        .formatted(src, dst)))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    Matchers.endsWith("/api/v1/admin/tag-implications/" + src + "/" + dst)))
        .andExpect(jsonPath("$.tagId").value(src.toString()))
        .andExpect(jsonPath("$.impliesTagId").value(dst.toString()));
  }

  @Test
  void post_unknownTag_returns_404() throws Exception {
    UUID src = UUID.randomUUID();
    UUID dst = UUID.randomUUID();
    when(createImplication.create(any(), any()))
        .thenThrow(new TagNotFoundException(new TagId(src)));

    mockMvc
        .perform(
            post("/api/v1/admin/tag-implications")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "tagId": "%s", "impliesTagId": "%s" }
                    """
                        .formatted(src, dst)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void post_missingField_returns_400_validation() throws Exception {
    UUID dst = UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/v1/admin/tag-implications")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "impliesTagId": "%s" }
                    """
                        .formatted(dst)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"));
    verifyNoInteractions(createImplication);
  }

  @Test
  void post_anonymous_returns_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tag-implications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "tagId": "%s", "impliesTagId": "%s" }
                    """
                        .formatted(UUID.randomUUID(), UUID.randomUUID())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void post_userRole_returns_403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tag-implications")
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "tagId": "%s", "impliesTagId": "%s" }
                    """
                        .formatted(UUID.randomUUID(), UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  @Test
  void delete_existing_returns_204() throws Exception {
    UUID src = UUID.randomUUID();
    UUID dst = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/admin/tag-implications/{src}/{dst}", src, dst).with(adminJwt()))
        .andExpect(status().isNoContent());

    verify(deleteImplication).delete(new TagId(src), new TagId(dst));
  }

  @Test
  void delete_missing_returns_404() throws Exception {
    UUID src = UUID.randomUUID();
    UUID dst = UUID.randomUUID();
    doThrow(new TagImplicationNotFoundException(new TagId(src), new TagId(dst)))
        .when(deleteImplication)
        .delete(new TagId(src), new TagId(dst));

    mockMvc
        .perform(delete("/api/v1/admin/tag-implications/{src}/{dst}", src, dst).with(adminJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void delete_userRole_returns_403() throws Exception {
    mockMvc
        .perform(
            delete(
                    "/api/v1/admin/tag-implications/{src}/{dst}",
                    UUID.randomUUID(),
                    UUID.randomUUID())
                .with(userJwt()))
        .andExpect(status().isForbidden());
    verifyNoInteractions(deleteImplication);
  }
}
