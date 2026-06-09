package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import java.time.Instant;
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

@WebMvcTest(RestaurantRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RestaurantRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateRestaurantUseCase createRestaurant;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void post_validPayload_returns_201_with_location_and_body() throws Exception {
    RestaurantId id = RestaurantId.newId();
    Restaurant created =
        new Restaurant(
            id,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT,
            null);
    when(createRestaurant.create(
            eq("Septime"), eq("80 Rue de Charonne"), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/user/restaurants")
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Septime",
                      "address": "80 Rue de Charonne"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string("Location", Matchers.endsWith("/api/v1/user/restaurants/" + id.value())))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.name").value("Septime"))
        .andExpect(jsonPath("$.address").value("80 Rue de Charonne"))
        .andExpect(jsonPath("$.location.latitude").value(48.8536))
        .andExpect(jsonPath("$.location.longitude").value(2.3795))
        .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT.toString()));
  }

  @Test
  void post_blankName_returns_400_problem_detail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants")
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "address": "80 Rue de Charonne"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
  }

  @Test
  void post_blankAddress_returns_400_problem_detail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants")
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Septime",
                      "address": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'address')]").exists());
  }

  @Test
  void post_unresolvableAddress_returns_422_problem_detail() throws Exception {
    when(createRestaurant.create(
            eq("Septime"), eq("nope nope nope"), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenThrow(new UnresolvableAddressException("nope nope nope"));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants")
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Septime",
                      "address": "nope nope nope"
                    }
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.title").value("Address could not be resolved"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unresolvable-address"))
        .andExpect(jsonPath("$.address").value("nope nope nope"));
  }
}
