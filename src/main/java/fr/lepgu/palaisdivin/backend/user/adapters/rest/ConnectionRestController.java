package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CreateConnectionUseCase;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/connections")
class ConnectionRestController {

  private final CreateConnectionUseCase createConnection;

  ConnectionRestController(CreateConnectionUseCase createConnection) {
    this.createConnection = createConnection;
  }

  @PostMapping("/{targetId}")
  ResponseEntity<ConnectionResponse> connect(
      @PathVariable UUID targetId, @AuthenticationPrincipal Jwt jwt) {
    ConnectionResult result = createConnection.connect(jwt.getSubject(), new UserId(targetId));
    ConnectionResponse body = ConnectionResponse.from(result.connection());
    if (result.created()) {
      URI location = URI.create("/api/v1/user/connections/" + targetId);
      return ResponseEntity.created(location).body(body);
    }
    return ResponseEntity.ok(body);
  }
}
