package fr.lepgu.palaisdivin.backend.photo.domain;

import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;

public final class PhotoNotFoundException extends RuntimeException {

  public PhotoNotFoundException(PhotoId id) {
    super("Photo not found: " + id.value());
  }
}
