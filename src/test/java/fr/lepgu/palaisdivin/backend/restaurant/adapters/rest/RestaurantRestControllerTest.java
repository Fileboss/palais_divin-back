package fr.lepgu.palaisdivin.backend.restaurant.adapters.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.CreateRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.ListRestaurantsUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
  @MockitoBean ListRestaurantsUseCase listRestaurants;

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
    when(createRestaurant.create(eq("Septime"), eq("80 Rue de Charonne"))).thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/public/restaurants")
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
            post("/api/v1/public/restaurants")
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
    when(createRestaurant.create(eq("Septime"), eq("nope nope nope")))
        .thenThrow(new UnresolvableAddressException("nope nope nope"));

    mockMvc
        .perform(
            post("/api/v1/public/restaurants")
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
  void list_decodesCursorAndForwardsToUseCase() throws Exception {
    Instant lastTs = Instant.parse("2026-05-27T10:15:30Z");
    UUID lastId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    String cursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"k\":\"" + lastTs + "\",\"id\":\"" + lastId + "\",\"v\":1}")
                    .getBytes(StandardCharsets.UTF_8));
    when(listRestaurants.list(new RestaurantCursor(lastTs, lastId), 5))
        .thenReturn(new CursorPage<>(List.of(restaurant("Septime")), false));

    mockMvc
        .perform(get("/api/v1/public/restaurants").param("cursor", cursor).param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void list_invalidCursor_returns400_problemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("cursor", "not!base64!!"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"))
        .andExpect(jsonPath("$.title").value("Invalid cursor"));
  }

  @Test
  void list_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_sizeBelowMin_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_unknownSortValue_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/restaurants").param("sort", "BOGUS"))
        .andExpect(status().isBadRequest());
  }

  private static Restaurant restaurant(String name) {
    return new Restaurant(
        RestaurantId.newId(), name, "addr", new Coordinates(48.8536, 2.3795), FIXED_CREATED_AT);
  }
}
