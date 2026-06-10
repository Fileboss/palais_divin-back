package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CheckFollowUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.FindUserUseCase;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/users")
class PublicUserRestController {

  private final FindUserUseCase findUser;
  private final CheckFollowUseCase checkFollow;

  PublicUserRestController(FindUserUseCase findUser, CheckFollowUseCase checkFollow) {
    this.findUser = findUser;
    this.checkFollow = checkFollow;
  }

  @GetMapping("/{userId}")
  PublicUserResponse get(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
    UserId targetId = new UserId(userId);
    User user = findUser.findById(targetId);
    Boolean isFollowedByMe =
        jwt == null ? null : checkFollow.isFollowedByViewer(jwt.getSubject(), targetId);
    return PublicUserResponse.from(user, isFollowedByMe);
  }
}
