package fr.lepgu.palaisdivin.backend.user.adapters.keycloak;

import fr.lepgu.palaisdivin.backend.config.KeycloakProperties;
import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class KeycloakTokenSupplier {

  private static final Duration REFRESH_BUFFER = Duration.ofSeconds(30);

  private final KeycloakTokenClient client;
  private final KeycloakProperties properties;
  private final Clock clock;
  private final ReentrantLock lock = new ReentrantLock();

  private volatile CachedToken cached;

  public KeycloakTokenSupplier(
      KeycloakTokenClient client, KeycloakProperties properties, Clock clock) {
    this.client = client;
    this.properties = properties;
    this.clock = clock;
  }

  public String currentBearer() {
    CachedToken snapshot = cached;
    Instant now = clock.instant();
    if (snapshot != null && now.isBefore(snapshot.refreshAt())) {
      return "Bearer " + snapshot.accessToken();
    }
    lock.lock();
    try {
      snapshot = cached;
      now = clock.instant();
      if (snapshot != null && now.isBefore(snapshot.refreshAt())) {
        return "Bearer " + snapshot.accessToken();
      }
      TokenResponse response;
      try {
        response =
            client.fetch("client_credentials", properties.clientId(), properties.clientSecret());
      } catch (RestClientException ex) {
        throw new KeycloakOperationException("Failed to fetch Keycloak admin token", ex);
      }
      if (response == null || response.accessToken() == null) {
        throw new KeycloakOperationException("Empty token response from Keycloak");
      }
      Instant refreshAt = now.plusSeconds(response.expiresInSeconds()).minus(REFRESH_BUFFER);
      cached = new CachedToken(response.accessToken(), refreshAt);
      return "Bearer " + response.accessToken();
    } finally {
      lock.unlock();
    }
  }

  private record CachedToken(String accessToken, Instant refreshAt) {}
}
