package fr.lepgu.palaisdivin.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.neo4j.autoconfigure.Neo4jProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class Neo4jConnectionTimeoutConfigTest {

  @Configuration
  @EnableConfigurationProperties(Neo4jProperties.class)
  static class PropertiesConfig {}

  @Test
  void applicationProperties_setsTwoSecondNeo4jConnectionTimeout() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(PropertiesConfig.class)
        .run(
            context ->
                assertThat(context.getBean(Neo4jProperties.class).getConnectionTimeout())
                    .isEqualTo(Duration.ofSeconds(2)));
  }
}
