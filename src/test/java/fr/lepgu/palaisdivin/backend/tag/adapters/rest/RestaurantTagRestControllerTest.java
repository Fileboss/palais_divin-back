package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.model.AttachResult;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.AttachTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DetachTagUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
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

@WebMvcTest(RestaurantTagRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RestaurantTagRestControllerTest {

  private static final Instant FIXED_AT = Instant.parse("2026-06-03T12:00:00Z");
  private static final String SUBJECT = "kc-subject-xyz";

  @Autowired MockMvc mockMvc;

  @MockitoBean AttachTagUseCase attachTag;
  @MockitoBean DetachTagUseCase detachTag;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void post_newAttachment_returns_201_with_location_and_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    UUID attachedBy = UUID.randomUUID();
    RestaurantTag attachment =
        new RestaurantTag(
            new RestaurantId(restaurantId), new TagId(tagId), new UserId(attachedBy), FIXED_AT);
    when(attachTag.attach(eq(SUBJECT), eq(new RestaurantId(restaurantId)), eq(new TagId(tagId))))
        .thenReturn(new AttachResult(attachment, true));

    mockMvc
        .perform(post("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId).with(userJwt()))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    Matchers.endsWith(
                        "/api/v1/user/restaurants/" + restaurantId + "/tags/" + tagId)))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
        .andExpect(jsonPath("$.tagId").value(tagId.toString()))
        .andExpect(jsonPath("$.attachedBy").value(attachedBy.toString()))
        .andExpect(jsonPath("$.attachedAt").value(FIXED_AT.toString()));
  }

  @Test
  void post_existingAttachment_returns_200_with_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    UUID attachedBy = UUID.randomUUID();
    RestaurantTag attachment =
        new RestaurantTag(
            new RestaurantId(restaurantId), new TagId(tagId), new UserId(attachedBy), FIXED_AT);
    when(attachTag.attach(eq(SUBJECT), eq(new RestaurantId(restaurantId)), eq(new TagId(tagId))))
        .thenReturn(new AttachResult(attachment, false));

    mockMvc
        .perform(post("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId).with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tagId").value(tagId.toString()));
  }

  @Test
  void post_unknownTag_returns_404_problem() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    when(attachTag.attach(eq(SUBJECT), eq(new RestaurantId(restaurantId)), eq(new TagId(tagId))))
        .thenThrow(new TagNotFoundException(new TagId(tagId)));

    mockMvc
        .perform(post("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId).with(userJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void post_anonymous_returns_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{r}/tags/{t}", UUID.randomUUID(), UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void delete_returns_204() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/v1/user/restaurants/{r}/tags/{t}", restaurantId, tagId).with(userJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void delete_anonymous_returns_401() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/user/restaurants/{r}/tags/{t}", UUID.randomUUID(), UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }
}
