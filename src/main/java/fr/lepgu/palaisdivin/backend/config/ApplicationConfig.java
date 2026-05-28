package fr.lepgu.palaisdivin.backend.config;

import fr.lepgu.palaisdivin.backend.shared.adapters.outbox.OutboxWorkerProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxWorkerProperties.class)
public class ApplicationConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
