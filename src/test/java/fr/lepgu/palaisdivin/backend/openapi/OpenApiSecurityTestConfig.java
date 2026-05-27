package fr.lepgu.palaisdivin.backend.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration(proxyBeanMethods = false)
public class OpenApiSecurityTestConfig {

  @Bean
  @Order(0)
  SecurityFilterChain openApiTestChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  OpenAPI palaisDivinOpenApi() {
    return new OpenAPI().info(new Info().title("Palais Divin Backend").version("0.0.1-SNAPSHOT"));
  }

  @Bean
  OpenApiCustomizer dropDynamicServers() {
    return openApi -> openApi.setServers(List.of());
  }
}
