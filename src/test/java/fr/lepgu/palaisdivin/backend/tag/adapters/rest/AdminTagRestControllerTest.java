package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagUseCase;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminTagRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminTagRestControllerTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-03T12:00:00Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateTagUseCase createTag;
  @MockitoBean DeleteTagUseCase deleteTag;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor adminJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static RequestPostProcessor userJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void post_returns_201_with_location_and_body() throws Exception {
    Tag created =
        new Tag(
            TagId.newId(),
            TagCategory.SPECIALTY,
            "natural-wine",
            "Natural wine",
            Map.of(),
            CREATED_AT);
    when(createTag.create(
            eq(TagCategory.SPECIALTY), eq("natural-wine"), eq("Natural wine"), eq(Map.of())))
        .thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "natural-wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location", Matchers.endsWith("/api/v1/admin/tags/" + created.id().value())))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(created.id().value().toString()))
        .andExpect(jsonPath("$.category").value("SPECIALTY"))
        .andExpect(jsonPath("$.slug").value("natural-wine"))
        .andExpect(jsonPath("$.label").value("Natural wine"))
        .andExpect(jsonPath("$.labelI18n").isMap())
        .andExpect(jsonPath("$.labelI18n").isEmpty())
        .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()));
  }

  @Test
  void post_persists_labelI18n_when_provided() throws Exception {
    Tag created =
        new Tag(
            TagId.newId(),
            TagCategory.REGIME,
            "vegan",
            "Végétalien",
            Map.of("en", "Vegan", "es", "Vegano"),
            CREATED_AT);
    when(createTag.create(
            eq(TagCategory.REGIME),
            eq("vegan"),
            eq("Végétalien"),
            eq(Map.of("en", "Vegan", "es", "Vegano"))))
        .thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "category": "REGIME",
                      "slug": "vegan",
                      "label": "Végétalien",
                      "labelI18n": { "en": "Vegan", "es": "Vegano" }
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.labelI18n.en").value("Vegan"))
        .andExpect(jsonPath("$.labelI18n.es").value("Vegano"));
  }

  @Test
  void post_badLocaleKey_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "category": "REGIME",
                      "slug": "vegan",
                      "label": "Végétalien",
                      "labelI18n": { "EN": "Vegan" }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"));

    verifyNoInteractions(createTag);
  }

  @Test
  void post_blankLocaleValue_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "category": "REGIME",
                      "slug": "vegan",
                      "label": "Végétalien",
                      "labelI18n": { "en": "" }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"));

    verifyNoInteractions(createTag);
  }

  @Test
  void post_blankSlug_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "", "label": "Natural wine" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'slug')]").exists());

    verifyNoInteractions(createTag);
  }

  @Test
  void post_slugWithUppercase_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "Natural-Wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'slug')]").exists());

    verifyNoInteractions(createTag);
  }

  @Test
  void post_slugWithSpace_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "natural wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'slug')]").exists());
  }

  @Test
  void post_unknownCategory_returns_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "BAR", "slug": "natural-wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(createTag);
  }

  @Test
  void post_blankLabel_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "natural-wine", "label": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'label')]").exists());

    verifyNoInteractions(createTag);
  }

  @Test
  void post_anonymous_returns_401_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "natural-wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));

    verifyNoInteractions(createTag);
  }

  @Test
  void post_userRole_returns_403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "natural-wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isForbidden());

    verifyNoInteractions(createTag);
  }

  @Test
  void post_duplicateSlug_returns_409_conflict() throws Exception {
    when(createTag.create(any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("uq_tag_slug"));

    mockMvc
        .perform(
            post("/api/v1/admin/tags")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "category": "SPECIALTY", "slug": "natural-wine", "label": "Natural wine" }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/conflict"));

    verify(createTag)
        .create(eq(TagCategory.SPECIALTY), eq("natural-wine"), eq("Natural wine"), eq(Map.of()));
  }

  @Test
  void delete_existing_returns_204() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/admin/tags/{id}", id).with(adminJwt()))
        .andExpect(status().isNoContent());

    verify(deleteTag).delete(new TagId(id));
  }

  @Test
  void delete_missing_returns_404_problem() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new TagNotFoundException(new TagId(id))).when(deleteTag).delete(new TagId(id));

    mockMvc
        .perform(delete("/api/v1/admin/tags/{id}", id).with(adminJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void delete_anonymous_returns_401() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/tags/{id}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(deleteTag);
  }

  @Test
  void delete_userRole_returns_403() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/tags/{id}", UUID.randomUUID()).with(userJwt()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(deleteTag);
  }
}
