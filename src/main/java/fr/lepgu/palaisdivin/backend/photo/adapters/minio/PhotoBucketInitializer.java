package fr.lepgu.palaisdivin.backend.photo.adapters.minio;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PhotoBucketInitializer {

  private static final Logger log = LoggerFactory.getLogger(PhotoBucketInitializer.class);

  @Bean
  ApplicationRunner ensurePhotoBucket(MinioClient client, MinioProperties properties) {
    return args -> {
      String bucket = properties.bucket();
      try {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
          client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
          log.info("Created MinIO bucket '{}'", bucket);
        }
      } catch (Exception e) {
        log.warn("Could not ensure MinIO bucket '{}' exists at startup: {}", bucket, e.getMessage());
      }
    };
  }
}
