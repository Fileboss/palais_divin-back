package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import fr.lepgu.palaisdivin.backend.shared.adapters.web.PageMeta;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionCursor;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CreateConnectionUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ListMyConnectionsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.LookupUsersUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RemoveConnectionUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/user/connections")
class ConnectionRestController {

  private final CreateConnectionUseCase createConnection;
  private final ListMyConnectionsUseCase listMyConnections;
  private final LookupUsersUseCase lookupUsers;
  private final RemoveConnectionUseCase removeConnection;

  ConnectionRestController(
      CreateConnectionUseCase createConnection,
      ListMyConnectionsUseCase listMyConnections,
      LookupUsersUseCase lookupUsers,
      RemoveConnectionUseCase removeConnection) {
    this.createConnection = createConnection;
    this.listMyConnections = listMyConnections;
    this.lookupUsers = lookupUsers;
    this.removeConnection = removeConnection;
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

  @GetMapping
  MyConnectionsPageResponse list(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @AuthenticationPrincipal Jwt jwt) {
    ConnectionCursor decoded = cursor == null ? null : ConnectionCursorCodec.decode(cursor);
    CursorPage<Connection> page = listMyConnections.listMine(jwt.getSubject(), decoded, size);

    List<UserId> targetIds = page.data().stream().map(Connection::targetUserId).toList();
    Map<UserId, User> usersById = lookupUsers.findByIds(targetIds);

    List<MyConnectionResponse> rows =
        page.data().stream()
            .map(
                c -> {
                  User u = usersById.get(c.targetUserId());
                  return u == null
                      ? null
                      : new MyConnectionResponse(
                          PublicUserResponse.from(u, Boolean.TRUE), c.createdAt());
                })
            .filter(Objects::nonNull)
            .toList();

    String nextCursor =
        page.hasNext() && !page.data().isEmpty()
            ? ConnectionCursorCodec.encode(
                new ConnectionCursor(page.data().getLast().createdAt(), page.data().getLast().id()))
            : null;

    return new MyConnectionsPageResponse(rows, new PageMeta(size, page.hasNext(), nextCursor));
  }

  @DeleteMapping("/{targetId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID targetId, @AuthenticationPrincipal Jwt jwt) {
    removeConnection.remove(jwt.getSubject(), new UserId(targetId));
  }
}
