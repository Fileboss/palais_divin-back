package fr.lepgu.palaisdivin.backend.photo.domain;

public final class PhotoStorageException extends RuntimeException {

  public PhotoStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
