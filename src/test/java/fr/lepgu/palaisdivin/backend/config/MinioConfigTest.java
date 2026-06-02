package fr.lepgu.palaisdivin.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.MinioClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MinioConfigTest {

  @Test
  void builds_minio_client_from_properties() {
    MinioProperties properties =
        new MinioProperties(
            "http://localhost:9000",
            "k",
            "s",
            Duration.ofSeconds(2),
            "palaisdivin-photos",
            Duration.ofMinutes(10),
            Duration.ofMinutes(10));

    MinioClient client = new MinioConfig().minioClient(properties);

    assertThat(client).isNotNull();
  }
}
