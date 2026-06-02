package fr.lepgu.palaisdivin.backend.photo.adapters.minio;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.PhotoStorageException;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
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

  private final MinioClient client;
  private final MinioProperties properties;

  PhotoMinioAdapter(MinioClient client, MinioProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public URI presignPut(String objectKey, Duration ttl) {
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
    }
  }
}
