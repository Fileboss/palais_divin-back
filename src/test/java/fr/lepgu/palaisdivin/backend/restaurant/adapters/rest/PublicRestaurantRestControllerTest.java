package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicRestaurantRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicRestaurantRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean FindRestaurantUseCase findRestaurant;
  @MockitoBean ListRestaurantsUseCase listRestaurants;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void get_existingId_returns_200_without_auth() throws Exception {
    RestaurantId id = RestaurantId.newId();
    Restaurant found =
        new Restaurant(
            id,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT,
            null);
    when(findRestaurant.findById(id)).thenReturn(Optional.of(found));

    mockMvc
        .perform(get("/api/v1/public/restaurants/{id}", id.value()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(id.value().toString()))
        .andExpect(jsonPath("$.name").value("Septime"))
        .andExpect(jsonPath("$.location.latitude").value(48.8536))
        .andExpect(jsonPath("$.location.longitude").value(2.3795));
  }

  @Test
  void get_missingId_returns_404_problem_detail() throws Exception {
    UUID id = UUID.randomUUID();
    when(findRestaurant.findById(new RestaurantId(id))).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/public/restaurants/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void list_noCursor_returnsEnvelope_withoutNextCursorWhenLastPage() throws Exception {
    Restaurant r1 = restaurant("Septime");
    Restaurant r2 = restaurant("Le Train Bleu");
    when(listRestaurants.list(null, 20)).thenReturn(new CursorPage<>(List.of(r1, r2), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].name").value("Septime"))
        .andExpect(jsonPath("$.data[1].name").value("Le Train Bleu"))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_withMoreAvailable_emitsNextCursor() throws Exception {
    Restaurant r1 = restaurant("Septime");
    Restaurant r2 = restaurant("Le Train Bleu");
    when(listRestaurants.list(null, 2)).thenReturn(new CursorPage<>(List.of(r1, r2), true));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.hasNext").value(true))
        .andExpect(
            jsonPath("$.page.nextCursor").value(Matchers.matchesPattern("^[A-Za-z0-9_\\-]+$")));
  }

  @Test
  void list_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  private static Restaurant restaurant(String name) {
    return new Restaurant(
        RestaurantId.newId(),
        name,
        "addr",
        new Coordinates(48.8536, 2.3795),
        FIXED_CREATED_AT,
        null);
  }
}
