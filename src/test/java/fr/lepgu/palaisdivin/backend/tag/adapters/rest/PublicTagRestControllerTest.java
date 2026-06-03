package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagsUseCase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicTagRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicTagRestControllerTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean ListTagsUseCase listTags;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void get_empty_returns_all_four_groups_empty() throws Exception {
    when(listTags.list()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/public/tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups", org.hamcrest.Matchers.hasSize(4)))
        .andExpect(jsonPath("$.groups[0].category").value("FOOD"))
        .andExpect(jsonPath("$.groups[0].tags", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.groups[1].category").value("REGIME"))
        .andExpect(jsonPath("$.groups[1].tags", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.groups[2].category").value("PLACE"))
        .andExpect(jsonPath("$.groups[2].tags", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.groups[3].category").value("VENUE_TYPE"))
        .andExpect(jsonPath("$.groups[3].tags", org.hamcrest.Matchers.hasSize(0)));
  }

  @Test
  void get_partial_returns_other_groups_empty() throws Exception {
    Tag t = new Tag(TagId.newId(), TagCategory.FOOD, "natural-wine", "Natural wine", NOW);
    when(listTags.list()).thenReturn(List.of(t));

    mockMvc
        .perform(get("/api/v1/public/tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups", org.hamcrest.Matchers.hasSize(4)))
        .andExpect(jsonPath("$.groups[0].category").value("FOOD"))
        .andExpect(jsonPath("$.groups[0].tags", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.groups[0].tags[0].slug").value("natural-wine"))
        .andExpect(jsonPath("$.groups[0].tags[0].label").value("Natural wine"))
        .andExpect(jsonPath("$.groups[1].tags", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.groups[2].tags", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.groups[3].tags", org.hamcrest.Matchers.hasSize(0)));
  }

  @Test
  void get_full_returns_one_per_category_in_enum_order() throws Exception {
    Tag food = new Tag(TagId.newId(), TagCategory.FOOD, "natural-wine", "Natural wine", NOW);
    Tag regime = new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", NOW);
    Tag place = new Tag(TagId.newId(), TagCategory.PLACE, "paris-11", "Paris 11e", NOW);
    Tag venue = new Tag(TagId.newId(), TagCategory.VENUE_TYPE, "bistro", "Bistrot", NOW);
    when(listTags.list()).thenReturn(List.of(food, regime, place, venue));

    mockMvc
        .perform(get("/api/v1/public/tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups", org.hamcrest.Matchers.hasSize(4)))
        .andExpect(jsonPath("$.groups[0].category").value("FOOD"))
        .andExpect(jsonPath("$.groups[0].tags[0].slug").value("natural-wine"))
        .andExpect(jsonPath("$.groups[1].category").value("REGIME"))
        .andExpect(jsonPath("$.groups[1].tags[0].slug").value("vegan"))
        .andExpect(jsonPath("$.groups[2].category").value("PLACE"))
        .andExpect(jsonPath("$.groups[2].tags[0].slug").value("paris-11"))
        .andExpect(jsonPath("$.groups[3].category").value("VENUE_TYPE"))
        .andExpect(jsonPath("$.groups[3].tags[0].slug").value("bistro"));
  }

  @Test
  void get_anonymous_allowed() throws Exception {
    when(listTags.list()).thenReturn(List.of());

    mockMvc.perform(get("/api/v1/public/tags")).andExpect(status().isOk());
  }
}
