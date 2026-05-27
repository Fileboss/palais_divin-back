package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RestaurantRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateRestaurantUseCase createRestaurant;
  @MockitoBean FindRestaurantUseCase findRestaurant;

  @Test
  void post_validPayload_returns_201_with_location_and_body() throws Exception {
    RestaurantId id = RestaurantId.newId();
    Restaurant created =
        new Restaurant(
            id,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT);
    when(createRestaurant.create(eq("Septime"), eq("80 Rue de Charonne"), any(Coordinates.class)))
        .thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/public/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Septime",
                      "address": "80 Rue de Charonne",
                      "location": { "latitude": 48.8536, "longitude": 2.3795 }
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string("Location", Matchers.endsWith("/api/v1/public/restaurants/" + id.value())))
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
            post("/api/v1/public/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "address": "x",
                      "location": { "latitude": 1.0, "longitude": 2.0 }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
  }

  @Test
  void post_latitudeOutOfRange_returns_400_problem_detail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Septime",
                      "address": "x",
                      "location": { "latitude": 91.0, "longitude": 2.0 }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'location.latitude')]").exists());
  }

  @Test
  void post_missingLocation_returns_400_problem_detail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Septime",
                      "address": "x"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'location')]").exists());
  }

  @Test
  void get_existingId_returns_200_with_body() throws Exception {
    RestaurantId id = RestaurantId.newId();
    Restaurant found =
        new Restaurant(
            id,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT);
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
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"))
        .andExpect(jsonPath("$.detail").value("Restaurant not found: " + id));
  }
}
