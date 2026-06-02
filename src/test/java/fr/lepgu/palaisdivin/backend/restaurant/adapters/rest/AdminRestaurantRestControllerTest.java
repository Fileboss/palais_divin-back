package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.DeleteRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminRestaurantRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminRestaurantRestControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean DeleteRestaurantUseCase deleteRestaurant;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor adminJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static RequestPostProcessor userJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void delete_existingId_returns204_andInvokesUseCase() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/admin/restaurants/{id}", id).with(adminJwt()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(deleteRestaurant).delete(new RestaurantId(id));
  }

  @Test
  void delete_missingId_returns404_problemDetail() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new RestaurantNotFoundException(new RestaurantId(id)))
        .when(deleteRestaurant)
        .delete(new RestaurantId(id));

    mockMvc
        .perform(delete("/api/v1/admin/restaurants/{id}", id).with(adminJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"))
        .andExpect(jsonPath("$.detail").value("Restaurant not found: " + id));
  }

  @Test
  void delete_anonymous_returns401() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/admin/restaurants/{id}", id))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(deleteRestaurant);
  }

  @Test
  void delete_userRole_returns403() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/admin/restaurants/{id}", id).with(userJwt()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(deleteRestaurant);
  }
}
