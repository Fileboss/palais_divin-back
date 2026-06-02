package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoDownloadUrlUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoUploadUrlUseCase;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.RegisterPhotoUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/user/restaurants/{restaurantId}/photos")
class PhotoRestController {

  private final MintPhotoUploadUrlUseCase mintUploadUrl;
  private final RegisterPhotoUseCase registerPhoto;
  private final MintPhotoDownloadUrlUseCase mintDownloadUrl;

  PhotoRestController(
      MintPhotoUploadUrlUseCase mintUploadUrl,
      RegisterPhotoUseCase registerPhoto,
      MintPhotoDownloadUrlUseCase mintDownloadUrl) {
    this.mintUploadUrl = mintUploadUrl;
    this.registerPhoto = registerPhoto;
    this.mintDownloadUrl = mintDownloadUrl;
  }

  @PostMapping("/upload-url")
  PhotoUploadUrlResponse mint(@PathVariable UUID restaurantId) {
    return PhotoUploadUrlResponse.from(mintUploadUrl.mint(new RestaurantId(restaurantId)));
  }

  @PostMapping
  ResponseEntity<PhotoResponse> register(
      @PathVariable UUID restaurantId,
      @Valid @RequestBody RegisterPhotoRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt) {
    Photo created =
        registerPhoto.register(
            jwt.getSubject(),
            new RestaurantId(restaurantId),
            req.objectKey(),
            req.contentType(),
            Optional.ofNullable(idempotencyKey));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id().value())
            .toUri();
    return ResponseEntity.created(location).body(PhotoResponse.from(created));
  }

  @GetMapping("/{photoId}/download-url")
  PhotoDownloadUrlResponse downloadUrl(
      @PathVariable UUID restaurantId, @PathVariable UUID photoId) {
    return PhotoDownloadUrlResponse.from(
        mintDownloadUrl.mint(new RestaurantId(restaurantId), new PhotoId(photoId)));
  }
}
