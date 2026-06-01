package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRestaurantAffinityUseCase;
import java.util.UUID;
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

@WebMvcTest(RestaurantAffinityRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RestaurantAffinityRestControllerTest {

  private static final String SUBJECT = "kc-subject-aff";

  @Autowired MockMvc mockMvc;

  @MockitoBean GetRestaurantAffinityUseCase getAffinity;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void get_returnsAffinity() throws Exception {
    UUID id = UUID.randomUUID();
    when(getAffinity.getFor(eq(SUBJECT), any(RestaurantId.class)))
        .thenReturn(new RestaurantAffinity(new RestaurantId(id), 9.0, 2));

    mockMvc
        .perform(get("/api/v1/user/restaurants/" + id + "/affinity").with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.restaurantId").value(id.toString()))
        .andExpect(jsonPath("$.affinity").value(9.0))
        .andExpect(jsonPath("$.recommenderCount").value(2));
  }

  @Test
  void get_unknownRestaurant_returns404_problemDetail() throws Exception {
    UUID id = UUID.randomUUID();
    when(getAffinity.getFor(eq(SUBJECT), any(RestaurantId.class)))
        .thenThrow(new RestaurantNotFoundException(new RestaurantId(id)));

    mockMvc
        .perform(get("/api/v1/user/restaurants/" + id + "/affinity").with(userJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void get_anonymous_returns401_problemDetail() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(get("/api/v1/user/restaurants/" + id + "/affinity"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }
}
