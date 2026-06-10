package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.SelfConnectionException;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CreateConnectionUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.ListMyConnectionsUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.LookupUsersUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RemoveConnectionUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ConnectionRestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ConnectionRestControllerTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-06-01T12:00:00Z");
  private static final String SUBJECT = "kc-subject-xyz";

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateConnectionUseCase createConnection;
  @MockitoBean ListMyConnectionsUseCase listMyConnections;
  @MockitoBean LookupUsersUseCase lookupUsers;
  @MockitoBean RemoveConnectionUseCase removeConnection;
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  private static Connection connection(UUID sourceId, UUID targetId) {
    return new Connection(
        ConnectionId.newId(), new UserId(sourceId), new UserId(targetId), FIXED_CREATED_AT);
  }

  private static User user(UUID id, String displayName) {
    return new User(
        new UserId(id), "subj-" + id, id + "@example.test", displayName, FIXED_CREATED_AT);
  }

  @Test
  void post_newConnection_returns201WithLocationAndBody() throws Exception {
    UUID targetId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    Connection conn = connection(sourceId, targetId);
    when(createConnection.connect(eq(SUBJECT), eq(new UserId(targetId))))
        .thenReturn(new ConnectionResult(conn, true));

    mockMvc
        .perform(post("/api/v1/user/connections/{targetId}", targetId).with(userJwt()))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", Matchers.endsWith("/api/v1/user/connections/" + targetId)))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(conn.id().value().toString()))
        .andExpect(jsonPath("$.sourceUserId").value(sourceId.toString()))
        .andExpect(jsonPath("$.targetUserId").value(targetId.toString()))
        .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT.toString()));
  }

  @Test
  void post_existingConnection_returns200WithBody() throws Exception {
    UUID targetId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    Connection conn = connection(sourceId, targetId);
    when(createConnection.connect(eq(SUBJECT), eq(new UserId(targetId))))
        .thenReturn(new ConnectionResult(conn, false));

    mockMvc
        .perform(post("/api/v1/user/connections/{targetId}", targetId).with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(conn.id().value().toString()));
  }

  @Test
  void post_targetMissing_returns404() throws Exception {
    UUID targetId = UUID.randomUUID();
    when(createConnection.connect(eq(SUBJECT), eq(new UserId(targetId))))
        .thenThrow(new UserNotFoundException(new UserId(targetId)));

    mockMvc
        .perform(post("/api/v1/user/connections/{targetId}", targetId).with(userJwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/not-found"));
  }

  @Test
  void post_selfConnection_returns422() throws Exception {
    UUID selfId = UUID.randomUUID();
    when(createConnection.connect(eq(SUBJECT), eq(new UserId(selfId))))
        .thenThrow(new SelfConnectionException(new UserId(selfId)));

    mockMvc
        .perform(post("/api/v1/user/connections/{targetId}", selfId).with(userJwt()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/self-connection"));
  }

  @Test
  void post_anonymous_returns401() throws Exception {
    mockMvc
        .perform(post("/api/v1/user/connections/{targetId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void list_emptyForCallerWithoutFollows() throws Exception {
    when(listMyConnections.listMine(eq(SUBJECT), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(), false));
    when(lookupUsers.findByIds(any())).thenReturn(Map.of());

    mockMvc
        .perform(get("/api/v1/user/connections").with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_returnsRowsWithEmbeddedUser() throws Exception {
    UUID sourceId = UUID.randomUUID();
    UUID targetUuid = UUID.randomUUID();
    Connection conn = connection(sourceId, targetUuid);
    User targetUser = user(targetUuid, "Alice");
    when(listMyConnections.listMine(eq(SUBJECT), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(conn), false));
    when(lookupUsers.findByIds(any())).thenReturn(Map.of(new UserId(targetUuid), targetUser));

    mockMvc
        .perform(get("/api/v1/user/connections").with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].user.id").value(targetUuid.toString()))
        .andExpect(jsonPath("$.data[0].user.displayName").value("Alice"))
        .andExpect(jsonPath("$.data[0].createdAt").value(FIXED_CREATED_AT.toString()))
        .andExpect(jsonPath("$.page.hasNext").value(false))
        .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
  }

  @Test
  void list_propagatesNextCursorWhenHasNext() throws Exception {
    UUID sourceId = UUID.randomUUID();
    UUID targetUuid = UUID.randomUUID();
    Connection conn = connection(sourceId, targetUuid);
    User targetUser = user(targetUuid, "Bob");
    when(listMyConnections.listMine(eq(SUBJECT), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(conn), true));
    when(lookupUsers.findByIds(any())).thenReturn(Map.of(new UserId(targetUuid), targetUser));

    mockMvc
        .perform(get("/api/v1/user/connections").with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.hasNext").value(true))
        .andExpect(jsonPath("$.page.nextCursor").isNotEmpty());
  }

  @Test
  void list_dropsStaleTargets() throws Exception {
    UUID sourceId = UUID.randomUUID();
    UUID targetUuid = UUID.randomUUID();
    Connection conn = connection(sourceId, targetUuid);
    when(listMyConnections.listMine(eq(SUBJECT), isNull(), eq(20)))
        .thenReturn(new CursorPage<>(List.of(conn), false));
    when(lookupUsers.findByIds(any())).thenReturn(Map.of()); // target vanished

    mockMvc
        .perform(get("/api/v1/user/connections").with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void list_400OnBadCursor() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/connections").param("cursor", "not!base64!!").with(userJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/invalid-cursor"));
  }

  @Test
  void list_400OnSizeOver100() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/connections").param("size", "101").with(userJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_400OnSizeUnderOne() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/connections").param("size", "0").with(userJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_anonymous_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/connections"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }

  @Test
  void delete_existing_returns204() throws Exception {
    UUID targetId = UUID.randomUUID();
    doNothing().when(removeConnection).remove(eq(SUBJECT), eq(new UserId(targetId)));

    mockMvc
        .perform(delete("/api/v1/user/connections/{targetId}", targetId).with(userJwt()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(removeConnection).remove(SUBJECT, new UserId(targetId));
  }

  @Test
  void delete_absent_returns204() throws Exception {
    UUID targetId = UUID.randomUUID();
    doNothing().when(removeConnection).remove(eq(SUBJECT), eq(new UserId(targetId)));

    mockMvc
        .perform(delete("/api/v1/user/connections/{targetId}", targetId).with(userJwt()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  void delete_self_returns422() throws Exception {
    UUID selfId = UUID.randomUUID();
    doThrow(new SelfConnectionException(new UserId(selfId)))
        .when(removeConnection)
        .remove(eq(SUBJECT), eq(new UserId(selfId)));

    mockMvc
        .perform(delete("/api/v1/user/connections/{targetId}", selfId).with(userJwt()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/self-connection"));
  }

  @Test
  void delete_anonymous_returns401() throws Exception {
    mockMvc
        .perform(delete("/api/v1/user/connections/{targetId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("https://palaisdivin.lepgu.fr/problems/unauthorized"));
  }
}
