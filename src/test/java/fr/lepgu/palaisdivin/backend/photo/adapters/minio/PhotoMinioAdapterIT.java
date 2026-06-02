package fr.lepgu.palaisdivin.backend.photo.adapters.minio;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PhotoMinioAdapterIT extends AbstractIntegrationTest {

  @Autowired PhotoStoragePort storage;

  @Test
  void presignPutReturnsUrlThatAcceptsActualUpload() throws IOException, InterruptedException {
    String objectKey = "restaurants/" + UUID.randomUUID() + "/" + UUID.randomUUID();

    URI url = storage.presignPut(objectKey, Duration.ofMinutes(5));

    assertThat(url.toString()).contains("palaisdivin-photos");
    assertThat(url.toString()).contains(objectKey);

    byte[] payload = "hello-minio".getBytes();
    HttpResponse<Void> put =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(url).PUT(BodyPublishers.ofByteArray(payload)).build(),
                BodyHandlers.discarding());

    assertThat(put.statusCode()).isEqualTo(200);
  }
}
