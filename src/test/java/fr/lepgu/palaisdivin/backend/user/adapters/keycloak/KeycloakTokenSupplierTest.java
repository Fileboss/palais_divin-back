package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.config.KeycloakProperties;
import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClientException;

class KeycloakTokenSupplierTest {

  private static final Instant T0 = Instant.parse("2026-05-29T10:00:00Z");

  private KeycloakTokenClient tokenClient;
  private MutableClock clock;
  private KeycloakTokenSupplier supplier;

  @BeforeEach
  void setUp() {
    tokenClient = Mockito.mock(KeycloakTokenClient.class);
    clock = new MutableClock(T0);
    KeycloakProperties properties =
        new KeycloakProperties(
            "http://kc.example",
            "palaisdivin",
            "palais-divin-backend",
            "secret",
            Duration.ofSeconds(2));
    supplier = new KeycloakTokenSupplier(tokenClient, properties, clock);
  }

  @Test
  void firstCallFetchesToken() {
    when(tokenClient.fetch(any(), any(), any())).thenReturn(new TokenResponse("token-1", 300L));

    String bearer = supplier.currentBearer();

    assertThat(bearer).isEqualTo("Bearer token-1");
    verify(tokenClient).fetch("client_credentials", "palais-divin-backend", "secret");
  }

  @Test
  void secondCallWithinTtlReusesCachedToken() {
    when(tokenClient.fetch(any(), any(), any())).thenReturn(new TokenResponse("token-1", 300L));

    supplier.currentBearer();
    clock.advance(Duration.ofSeconds(60));
    String bearer = supplier.currentBearer();

    assertThat(bearer).isEqualTo("Bearer token-1");
    verify(tokenClient, times(1)).fetch(any(), any(), any());
  }

  @Test
  void refreshesAfterTtlExpiry() {
    when(tokenClient.fetch(any(), any(), any()))
        .thenReturn(new TokenResponse("token-1", 60L))
        .thenReturn(new TokenResponse("token-2", 60L));

    supplier.currentBearer();
    clock.advance(Duration.ofSeconds(31));
    String bearer = supplier.currentBearer();

    assertThat(bearer).isEqualTo("Bearer token-2");
    verify(tokenClient, times(2)).fetch(any(), any(), any());
  }

  @Test
  void restClientExceptionSurfacesAsKeycloakOperationException() {
    when(tokenClient.fetch(any(), any(), any())).thenThrow(new RestClientException("boom"));

    assertThatThrownBy(() -> supplier.currentBearer())
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("Failed to fetch Keycloak admin token");
  }

  @Test
  void emptyAccessTokenSurfacesAsKeycloakOperationException() {
    when(tokenClient.fetch(any(), any(), any())).thenReturn(new TokenResponse(null, 60L));

    assertThatThrownBy(() -> supplier.currentBearer())
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("Empty token response");
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration d) {
      this.now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
