package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lepgu.palaisdivin.backend.config.security.SecurityConfig;
import fr.lepgu.palaisdivin.backend.shared.adapters.web.GlobalExceptionHandler;
import fr.lepgu.palaisdivin.backend.user.domain.SelfConnectionException;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionId;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionResult;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.CreateConnectionUseCase;
import java.time.Instant;
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
  @MockitoBean JwtDecoder jwtDecoder;

  private static RequestPostProcessor userJwt() {
    return jwt().jwt(j -> j.subject(SUBJECT)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  private static Connection connection(UUID sourceId, UUID targetId) {
    return new Connection(
        ConnectionId.newId(), new UserId(sourceId), new UserId(targetId), FIXED_CREATED_AT);
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
}
