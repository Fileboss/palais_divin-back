package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagImplicationsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicTagImplicationRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicTagImplicationRestControllerTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean ListTagImplicationsUseCase listImplications;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void get_empty_returns_200_with_empty_data() throws Exception {
    when(listImplications.listAll()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/public/tag-implications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(0)));
  }

  @Test
  void get_returns_seeded_rows() throws Exception {
    UUID src = UUID.randomUUID();
    UUID dst = UUID.randomUUID();
    when(listImplications.listAll())
        .thenReturn(List.of(new TagImplication(new TagId(src), new TagId(dst), NOW)));

    mockMvc
        .perform(get("/api/v1/public/tag-implications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.data[0].tagId").value(src.toString()))
        .andExpect(jsonPath("$.data[0].impliesTagId").value(dst.toString()));
  }
}
