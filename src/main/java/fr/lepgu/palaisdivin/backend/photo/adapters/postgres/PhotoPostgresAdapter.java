package fr.lepgu.palaisdivin.backend.photo.adapters.postgres;

import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoRepositoryPort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PhotoPostgresAdapter implements PhotoRepositoryPort {

  private final PhotoJpaRepository jpa;
  @PersistenceContext private EntityManager em;

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

  @Override
  public Map<RestaurantId, Photo> findOldestByRestaurantIds(
      Collection<RestaurantId> restaurantIds) {
    if (restaurantIds.isEmpty()) {
      return Map.of();
    }
    List<UUID> raw = restaurantIds.stream().map(RestaurantId::value).toList();
    Query q =
        em.createNativeQuery(
            "select distinct on (restaurant_id) * "
                + "from photo "
                + "where restaurant_id in (:ids) "
                + "order by restaurant_id, created_at asc, id asc",
            PhotoEntity.class);
    q.setParameter("ids", raw);
    @SuppressWarnings("unchecked")
    List<PhotoEntity> rows = q.getResultList();
    Map<RestaurantId, Photo> result = new LinkedHashMap<>(rows.size());
    for (PhotoEntity e : rows) {
      Photo p = toDomain(e);
      result.put(p.restaurantId(), p);
    }
    return Map.copyOf(result);
  }

  @Override
  public CursorPage<Photo> findByRestaurantId(
      RestaurantId restaurantId, PhotoCursor cursor, int size) {
    StringBuilder sql = new StringBuilder();
    sql.append("select * from photo where restaurant_id = :rid ");
    if (cursor != null) {
      sql.append("and (created_at > :ck or (created_at = :ck and id > :cid)) ");
    }
    sql.append("order by created_at asc, id asc");

    Query q = em.createNativeQuery(sql.toString(), PhotoEntity.class);
    q.setParameter("rid", restaurantId.value());
    if (cursor != null) {
      q.setParameter("ck", cursor.createdAt());
      q.setParameter("cid", cursor.id());
    }
    q.setMaxResults(size + 1);

    @SuppressWarnings("unchecked")
    List<PhotoEntity> rows = q.getResultList();
    List<Photo> hydrated = rows.stream().map(PhotoPostgresAdapter::toDomain).toList();
    boolean hasNext = hydrated.size() > size;
    List<Photo> page = hasNext ? hydrated.subList(0, size) : hydrated;
    return new CursorPage<>(page, hasNext);
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
