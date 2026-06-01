package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRestaurantAffinityUseCase;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/restaurants")
class RestaurantAffinityRestController {

  private final GetRestaurantAffinityUseCase getAffinity;

  RestaurantAffinityRestController(GetRestaurantAffinityUseCase getAffinity) {
    this.getAffinity = getAffinity;
  }

  @GetMapping("/{id}/affinity")
  RestaurantAffinityResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return RestaurantAffinityResponse.from(
        getAffinity.getFor(jwt.getSubject(), new RestaurantId(id)));
  }
}
