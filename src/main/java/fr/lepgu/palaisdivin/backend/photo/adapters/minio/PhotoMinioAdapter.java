package fr.lepgu.palaisdivin.backend.photo.adapters.minio;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.PhotoStorageException;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class PhotoMinioAdapter implements PhotoStoragePort {

  private static final String PRESIGN_TIMER = "palaisdivin.minio.presign";

  private final MinioClient client;
  private final MinioProperties properties;
  private final MeterRegistry meterRegistry;
  private final Timer putTimer;
  private final Timer getTimer;

  PhotoMinioAdapter(MinioClient client, MinioProperties properties, MeterRegistry meterRegistry) {
    this.client = client;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.putTimer =
        Timer.builder(PRESIGN_TIMER)
            .description("Latency of MinIO presigned URL minting")
            .tag("operation", "put")
            .register(meterRegistry);
    this.getTimer =
        Timer.builder(PRESIGN_TIMER)
            .description("Latency of MinIO presigned URL minting")
            .tag("operation", "get")
            .register(meterRegistry);
  }

  @Override
  public URI presignPut(String objectKey, Duration ttl) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      String url =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(properties.bucket())
                  .object(objectKey)
                  .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                  .build());
      return URI.create(url);
    } catch (MinioException | GeneralSecurityException | IOException e) {
      throw new PhotoStorageException("Failed to mint presigned PUT URL for " + objectKey, e);
    } finally {
      sample.stop(putTimer);
    }
  }

  @Override
  public URI presignGet(String objectKey, Duration ttl) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      String url =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(properties.bucket())
                  .object(objectKey)
                  .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                  .build());
      return URI.create(url);
    } catch (MinioException | GeneralSecurityException | IOException e) {
      throw new PhotoStorageException("Failed to mint presigned GET URL for " + objectKey, e);
    } finally {
      sample.stop(getTimer);
    }
  }
}
