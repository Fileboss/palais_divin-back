package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.FindUserUseCase;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/users")
class PublicUserRestController {

  private final FindUserUseCase findUser;

  PublicUserRestController(FindUserUseCase findUser) {
    this.findUser = findUser;
  }

  @GetMapping("/{userId}")
  PublicUserResponse get(@PathVariable UUID userId) {
    return PublicUserResponse.from(findUser.findById(new UserId(userId)));
  }
}
