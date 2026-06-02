package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.photo.domain.InvalidObjectKeyException;
import fr.lepgu.palaisdivin.backend.photo.domain.PhotoNotFoundException;
import fr.lepgu.palaisdivin.backend.photo.domain.PhotoStorageException;
import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoDownloadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoUploadUrl;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoDownloadUrlUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoUploadUrlUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.RegisterPhotoUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PhotoRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PhotoRestControllerTest {

  private static final Instant FIXED_EXPIRES_AT = Instant.parse("2026-06-02T12:10:00Z");
  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-06-02T12:05:00Z");
  private static final String SUBJECT = "kc-subject-xyz";

  @Autowired MockMvc mockMvc;

  @MockitoBean MintPhotoUploadUrlUseCase mintUploadUrl;
  @MockitoBean RegisterPhotoUseCase registerPhoto;
  @MockitoBean MintPhotoDownloadUrlUseCase mintDownloadUrl;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  void mint_returns_200_with_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    String objectKey = "restaurants/" + restaurantId + "/" + UUID.randomUUID();
    URI uploadUrl = URI.create("http://minio.test/palaisdivin-photos/" + objectKey + "?sig=abc");
    when(mintUploadUrl.mint(new RestaurantId(restaurantId)))
        .thenReturn(new PhotoUploadUrl(objectKey, uploadUrl, FIXED_EXPIRES_AT));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos/upload-url", restaurantId).with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.objectKey").value(objectKey))
        .andExpect(jsonPath("$.uploadUrl").value(uploadUrl.toString()))
        .andExpect(jsonPath("$.expiresAt").value(FIXED_EXPIRES_AT.toString()));
  }

  @Test
  void mint_restaurantMissing_returns_404_problem() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    when(mintUploadUrl.mint(any()))
        .thenThrow(new RestaurantNotFoundException(new RestaurantId(restaurantId)));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos/upload-url", restaurantId).with(userJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void mint_storageFailure_returns_502_problem() throws Exception {
    when(mintUploadUrl.mint(any()))
        .thenThrow(new PhotoStorageException("boom", new RuntimeException()));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos/upload-url", UUID.randomUUID())
                .with(userJwt()))
        .andExpect(status().isBadGateway())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/upstream-failure"));
  }

  @Test
  void mint_anonymous_returns_401_problem() throws Exception {
    mockMvc
        .perform(post("/api/v1/user/restaurants/{rid}/photos/upload-url", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void register_returns_201_with_location_and_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    String objectKey = "restaurants/" + restaurantId + "/" + UUID.randomUUID();
    Photo created =
        new Photo(
            PhotoId.newId(),
            new RestaurantId(restaurantId),
            UserId.newId(),
            objectKey,
            "image/jpeg",
            FIXED_CREATED_AT);
    when(registerPhoto.register(
            eq(SUBJECT),
            eq(new RestaurantId(restaurantId)),
            eq(objectKey),
            eq("image/jpeg"),
            eq(Optional.empty())))
        .thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "objectKey": "%s", "contentType": "image/jpeg" }
                    """
                        .formatted(objectKey)))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    Matchers.endsWith(
                        "/api/v1/user/restaurants/"
                            + restaurantId
                            + "/photos/"
                            + created.id().value())))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(created.id().value().toString()))
        .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
        .andExpect(jsonPath("$.objectKey").value(objectKey))
        .andExpect(jsonPath("$.contentType").value("image/jpeg"))
        .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT.toString()));
  }

  @Test
  void register_blankObjectKey_returns_400_validation_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos", UUID.randomUUID())
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "objectKey": "", "contentType": "image/jpeg" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field == 'objectKey')]").exists());
  }

  @Test
  void register_invalidObjectKeyShape_returns_400_problem() throws Exception {
    when(registerPhoto.register(any(), any(), any(), any(), any()))
        .thenThrow(new InvalidObjectKeyException("Object key must match restaurants/{id}/{uuid}"));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos", UUID.randomUUID())
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "objectKey": "bogus", "contentType": "image/jpeg" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-object-key"));
  }

  @Test
  void register_restaurantMissing_returns_404_problem() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    when(registerPhoto.register(any(), any(), any(), any(), any()))
        .thenThrow(new RestaurantNotFoundException(new RestaurantId(restaurantId)));

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos", restaurantId)
                .with(userJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "objectKey": "restaurants/%s/abc", "contentType": "image/jpeg" }
                    """
                        .formatted(restaurantId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void register_anonymous_returns_401_problem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "objectKey": "x", "contentType": "image/jpeg" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void register_forwardsIdempotencyKeyHeader() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    String objectKey = "restaurants/" + restaurantId + "/" + UUID.randomUUID();
    Photo created =
        new Photo(
            PhotoId.newId(),
            new RestaurantId(restaurantId),
            UserId.newId(),
            objectKey,
            "image/jpeg",
            FIXED_CREATED_AT);
    when(registerPhoto.register(any(), any(), any(), any(), any())).thenReturn(created);

    mockMvc
        .perform(
            post("/api/v1/user/restaurants/{rid}/photos", restaurantId)
                .with(userJwt())
                .header("Idempotency-Key", "KEY-XYZ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "objectKey": "%s", "contentType": "image/jpeg" }
                    """
                        .formatted(objectKey)))
        .andExpect(status().isCreated());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Optional<String>> keyCaptor = ArgumentCaptor.forClass(Optional.class);
    verify(registerPhoto)
        .register(
            eq(SUBJECT),
            eq(new RestaurantId(restaurantId)),
            eq(objectKey),
            eq("image/jpeg"),
            keyCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(keyCaptor.getValue()).contains("KEY-XYZ");
  }

  @Test
  void downloadUrl_returns_200_with_body() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    String objectKey = "restaurants/" + restaurantId + "/" + UUID.randomUUID();
    URI signedUrl = URI.create("http://minio.test/palaisdivin-photos/" + objectKey + "?sig=get");
    when(mintDownloadUrl.mint(new RestaurantId(restaurantId), new PhotoId(photoId)))
        .thenReturn(new PhotoDownloadUrl(objectKey, signedUrl, FIXED_EXPIRES_AT));

    mockMvc
        .perform(
            get("/api/v1/user/restaurants/{rid}/photos/{pid}/download-url", restaurantId, photoId)
                .with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.objectKey").value(objectKey))
        .andExpect(jsonPath("$.downloadUrl").value(signedUrl.toString()))
        .andExpect(jsonPath("$.expiresAt").value(FIXED_EXPIRES_AT.toString()));
  }

  @Test
  void downloadUrl_photoMissing_returns_404_problem() throws Exception {
    UUID photoId = UUID.randomUUID();
    when(mintDownloadUrl.mint(any(), any()))
        .thenThrow(new PhotoNotFoundException(new PhotoId(photoId)));

    mockMvc
        .perform(
            get(
                    "/api/v1/user/restaurants/{rid}/photos/{pid}/download-url",
                    UUID.randomUUID(),
                    photoId)
                .with(userJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void downloadUrl_anonymous_returns_401_problem() throws Exception {
    mockMvc
        .perform(
            get(
                "/api/v1/user/restaurants/{rid}/photos/{pid}/download-url",
                UUID.randomUUID(),
                UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }
}
