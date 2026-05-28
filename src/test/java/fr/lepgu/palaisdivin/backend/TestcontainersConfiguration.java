package fr.lepgu.palaisdivin.backend;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  Neo4jContainer neo4jContainer() {
    return new Neo4jContainer(DockerImageName.parse("neo4j:5.20-enterprise"))
        .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes");
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
  }

  // Test-token decoder: bridge between M3.3 (no real Keycloak) and M3.4 (testcontainers-keycloak).
  @Bean
  JwtDecoder testJwtDecoder() {
    return token ->
        switch (token) {
          case "test-user" -> jwt(token, "test-user-sub", List.of("USER"));
          case "test-admin" -> jwt(token, "test-admin-sub", List.of("ADMIN"));
          default -> throw new BadJwtException("Unknown test token: " + token);
        };
  }

  private static Jwt jwt(String token, String sub, List<String> roles) {
    return Jwt.withTokenValue(token)
        .header("alg", "none")
        .subject(sub)
        .claim("realm_access", Map.of("roles", roles))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();
  }
}
