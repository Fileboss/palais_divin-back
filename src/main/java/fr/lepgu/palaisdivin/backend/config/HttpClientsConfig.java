package fr.lepgu.palaisdivin.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding.BanApiClient;
import java.net.http.HttpClient;
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
@EnableConfigurationProperties(BanProperties.class)
public class HttpClientsConfig {

  static final String GEOCODE_CACHE = "geocode";

  @Bean
  BanApiClient banApiClient(BanProperties properties) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.timeout());
    RestClient restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
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
}
