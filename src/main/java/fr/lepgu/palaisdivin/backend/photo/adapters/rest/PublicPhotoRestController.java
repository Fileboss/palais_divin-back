package fr.lepgu.palaisdivin.backend.photo.adapters.rest;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummaryPage;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.ListPublicRestaurantPhotosUseCase;
import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.FindRestaurantUseCase;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/restaurants/{restaurantId}/photos")
class PublicPhotoRestController {

  private final ListPublicRestaurantPhotosUseCase listPhotos;
  private final FindRestaurantUseCase findRestaurant;

  PublicPhotoRestController(
      ListPublicRestaurantPhotosUseCase listPhotos, FindRestaurantUseCase findRestaurant) {
    this.listPhotos = listPhotos;
    this.findRestaurant = findRestaurant;
  }

  @GetMapping
  PhotosPageResponse list(
      @PathVariable UUID restaurantId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    RestaurantId rid = new RestaurantId(restaurantId);
    if (findRestaurant.findById(rid).isEmpty()) {
      throw new RestaurantNotFoundException(rid);
    }
    PhotoCursor decoded = cursor == null ? null : PhotoCursorCodec.decode(cursor);
    PhotoSummaryPage page = listPhotos.list(rid, decoded, size);
    List<PhotoSummaryResponse> data =
        page.data().stream().map(PhotoSummaryResponse::from).toList();
    String nextCursor =
        page.nextCursor() == null ? null : PhotoCursorCodec.encode(page.nextCursor());
    return new PhotosPageResponse(data, new PageMeta(size, page.hasNext(), nextCursor));
  }
}
