package fr.lepgu.palaisdivin.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
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

  @Bean
  JwtDecoder stubJwtDecoder() {
    return token -> {
      throw new BadJwtException("Stub JwtDecoder — no real Keycloak in this IT");
    };
  }
}
