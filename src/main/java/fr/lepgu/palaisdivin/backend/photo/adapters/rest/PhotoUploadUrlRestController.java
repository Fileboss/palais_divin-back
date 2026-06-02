package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.ports.MintPhotoUploadUrlUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/restaurants/{restaurantId}/photos")
class PhotoUploadUrlRestController {

  private final MintPhotoUploadUrlUseCase mintUploadUrl;

  PhotoUploadUrlRestController(MintPhotoUploadUrlUseCase mintUploadUrl) {
    this.mintUploadUrl = mintUploadUrl;
  }

  @PostMapping("/upload-url")
  PhotoUploadUrlResponse mint(@PathVariable UUID restaurantId) {
    return PhotoUploadUrlResponse.from(mintUploadUrl.mint(new RestaurantId(restaurantId)));
  }
}
