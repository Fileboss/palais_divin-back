package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.AttachResult;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.AttachTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DetachTagUseCase;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/user/restaurants/{restaurantId}/tags")
class RestaurantTagRestController {

  private final AttachTagUseCase attachTag;
  private final DetachTagUseCase detachTag;

  RestaurantTagRestController(AttachTagUseCase attachTag, DetachTagUseCase detachTag) {
    this.attachTag = attachTag;
    this.detachTag = detachTag;
  }

  @PostMapping("/{tagId}")
  ResponseEntity<RestaurantTagResponse> attach(
      @PathVariable UUID restaurantId, @PathVariable UUID tagId, @AuthenticationPrincipal Jwt jwt) {
    AttachResult result =
        attachTag.attach(jwt.getSubject(), new RestaurantId(restaurantId), new TagId(tagId));
    RestaurantTagResponse body = RestaurantTagResponse.from(result.attachment());
    if (result.created()) {
      URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
      return ResponseEntity.created(location).body(body);
    }
    return ResponseEntity.ok(body);
  }

  @DeleteMapping("/{tagId}")
  ResponseEntity<Void> detach(@PathVariable UUID restaurantId, @PathVariable UUID tagId) {
    detachTag.detach(new RestaurantId(restaurantId), new TagId(tagId));
    return ResponseEntity.noContent().build();
  }
}
