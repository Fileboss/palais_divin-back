package fr.lepgu.palaisdivin.backend;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  static final String TEST_BUCKET = "palaisdivin-photos";

  private static final Neo4jContainer NEO4J =
      new Neo4jContainer(DockerImageName.parse("neo4j:5.20-enterprise"))
          .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
          .withReuse(true);

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
          .withCommand("postgres", "-c", "max_connections=300")
          .withReuse(true);

  private static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer().withRealmImportFile("/realm-palaisdivin.json");

  private static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:latest")
          .withUserName("testuser")
          .withPassword("testpassword");

  static {
    Startables.deepStart(NEO4J, POSTGRES, KEYCLOAK, MINIO).join();
    try {
      MinioClient seedClient =
          MinioClient.builder()
              .endpoint(MINIO.getS3URL())
              .credentials(MINIO.getUserName(), MINIO.getPassword())
              .build();
      if (!seedClient.bucketExists(BucketExistsArgs.builder().bucket(TEST_BUCKET).build())) {
        seedClient.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET).build());
      }
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Bean
  @ServiceConnection
  Neo4jContainer neo4jContainer() {
    return NEO4J;
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return POSTGRES;
  }

  @Bean
  KeycloakContainer keycloakContainer() {
    return KEYCLOAK;
  }

  @Bean
  MinIOContainer minioContainer() {
    return MINIO;
  }

  @Bean
  DynamicPropertyRegistrar keycloakIssuerUriRegistrar() {
    return registry ->
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> KEYCLOAK.getAuthServerUrl() + "/realms/palaisdivin");
  }

  @Bean
  DynamicPropertyRegistrar keycloakAdminPropsRegistrar() {
    return registry -> {
      registry.add("app.keycloak.base-url", KEYCLOAK::getAuthServerUrl);
      registry.add("app.keycloak.realm", () -> "palaisdivin");
      registry.add("app.keycloak.client-id", () -> "palais-divin-backend");
      registry.add("app.keycloak.client-secret", () -> "test-backend-secret");
    };
  }

  @Bean
  DynamicPropertyRegistrar minioPropsRegistrar() {
    return registry -> {
      registry.add("app.minio.endpoint", MINIO::getS3URL);
      registry.add("app.minio.access-key", MINIO::getUserName);
      registry.add("app.minio.secret-key", MINIO::getPassword);
    };
  }
}
