package fr.lepgu.palaisdivin.backend;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
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
  KeycloakContainer keycloakContainer() {
    return new KeycloakContainer().withRealmImportFile("/realm-palaisdivin.json");
  }

  @Bean
  DynamicPropertyRegistrar keycloakIssuerUriRegistrar(KeycloakContainer keycloak) {
    return registry ->
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloak.getAuthServerUrl() + "/realms/palaisdivin");
  }
}
