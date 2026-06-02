package fr.lepgu.palaisdivin.backend.photo.adapters.postgres;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoRepositoryPort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PhotoPostgresAdapter implements PhotoRepositoryPort {

  private final PhotoJpaRepository jpa;

  PhotoPostgresAdapter(PhotoJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Photo save(Photo photo) {
    return toDomain(jpa.save(toEntity(photo)));
  }

  @Override
  public Optional<Photo> findById(PhotoId id) {
    return jpa.findById(id.value()).map(PhotoPostgresAdapter::toDomain);
  }

  private static PhotoEntity toEntity(Photo p) {
    return new PhotoEntity(
        p.id().value(),
        p.restaurantId().value(),
        p.authorId().value(),
        p.objectKey(),
        p.contentType(),
        p.createdAt());
  }

  private static Photo toDomain(PhotoEntity e) {
    return new Photo(
        new PhotoId(e.getId()),
        new RestaurantId(e.getRestaurantId()),
        new UserId(e.getAuthorId()),
        e.getObjectKey(),
        e.getContentType(),
        e.getCreatedAt());
  }
}
