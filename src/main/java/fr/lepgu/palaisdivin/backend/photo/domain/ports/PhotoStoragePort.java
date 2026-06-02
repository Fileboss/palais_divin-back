package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import java.net.URI;
import java.time.Duration;

public interface PhotoStoragePort {

  URI presignPut(String objectKey, Duration ttl);

  URI presignGet(String objectKey, Duration ttl);
}
