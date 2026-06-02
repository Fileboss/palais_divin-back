package fr.lepgu.palaisdivin.backend.photo.domain.ports;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import java.util.Optional;

public interface PhotoRepositoryPort {

  Photo save(Photo photo);

  Optional<Photo> findById(PhotoId id);
}
