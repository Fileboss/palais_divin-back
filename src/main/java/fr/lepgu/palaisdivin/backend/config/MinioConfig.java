package fr.lepgu.palaisdivin.backend.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

  @Bean
  MinioClient minioClient(MinioProperties properties) {
    OkHttpClient httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(properties.timeout())
            .readTimeout(properties.timeout())
            .writeTimeout(properties.timeout())
            .build();
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .httpClient(httpClient)
        .build();
  }
}
