package fr.lepgu.palaisdivin.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanApiClient;
import fr.lepgu.palaisdivin.backend.user.adapters.keycloak.KeycloakAdminClient;
import fr.lepgu.palaisdivin.backend.user.adapters.keycloak.KeycloakTokenClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@EnableCaching
@EnableConfigurationProperties({BanProperties.class, KeycloakProperties.class})
public class HttpClientsConfig {

  static final String GEOCODE_CACHE = "geocode";

  @Bean
  BanApiClient banApiClient(BanProperties properties) {
    RestClient restClient = restClient(properties.baseUrl(), properties.timeout());
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(BanApiClient.class);
  }

  @Bean
  CacheManager cacheManager(BanProperties properties) {
    CaffeineCacheManager manager = new CaffeineCacheManager(GEOCODE_CACHE);
    manager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(properties.cacheTtl()).recordStats());
    return manager;
  }

  @Bean
  KeycloakTokenClient keycloakTokenClient(KeycloakProperties properties) {
    RestClient restClient =
        restClient(properties.baseUrl() + "/realms/" + properties.realm(), properties.timeout());
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(KeycloakTokenClient.class);
  }

  @Bean
  KeycloakAdminClient keycloakAdminClient(KeycloakProperties properties) {
    RestClient restClient =
        restClient(
            properties.baseUrl() + "/admin/realms/" + properties.realm(), properties.timeout());
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(KeycloakAdminClient.class);
  }

  private static RestClient restClient(String baseUrl, Duration timeout) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
