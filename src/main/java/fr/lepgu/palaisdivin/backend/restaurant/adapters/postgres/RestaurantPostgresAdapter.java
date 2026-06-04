package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantPostgresAdapter implements RestaurantRepositoryPort {

  private static final int SRID_WGS84 = 4326;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  private final RestaurantJpaRepository jpa;
  @PersistenceContext private EntityManager em;

  RestaurantPostgresAdapter(RestaurantJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Restaurant save(Restaurant restaurant) {
    return toDomain(jpa.save(toEntity(restaurant)));
  }

  @Override
  public Optional<Restaurant> findById(RestaurantId id) {
    return jpa.findById(id.value()).map(RestaurantPostgresAdapter::toDomain);
  }

  @Override
  public Map<RestaurantId, Restaurant> findByIds(Collection<RestaurantId> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UUID> raw = ids.stream().map(RestaurantId::value).toList();
    return jpa.findAllById(raw).stream()
        .map(RestaurantPostgresAdapter::toDomain)
        .collect(Collectors.toUnmodifiableMap(Restaurant::id, r -> r));
  }

  @Override
  public void deleteById(RestaurantId id) {
    jpa.deleteById(id.value());
  }

  @Override
  public CursorPage<Restaurant> findAll(
      RestaurantCursor cursor, int size, RestaurantFilter filter, RestaurantSort sort) {
    StringBuilder sql = new StringBuilder("select r.* from restaurant r ");
    if (filter.hasTags()) {
      sql.append(
          "join restaurant_tag rt on rt.restaurant_id = r.id " + "join tag t on rt.tag_id = t.id ");
    }
    sql.append("where 1=1 ");
    if (filter.hasTags()) {
      sql.append("and t.slug in (:slugs) ");
    }
    if (filter.hasName()) {
      sql.append("and r.name ilike :namePattern ");
    }
    appendKeysetPredicate(sql, cursor, sort);
    if (filter.hasTags()) {
      sql.append("group by r.id having count(distinct rt.tag_id) = :slugCount ");
    }
    sql.append(orderByClause(sort));

    Query q = em.createNativeQuery(sql.toString(), RestaurantEntity.class);
    if (filter.hasTags()) {
      q.setParameter("slugs", filter.tagSlugs());
      q.setParameter("slugCount", filter.tagSlugs().size());
    }
    if (filter.hasName()) {
      q.setParameter("namePattern", "%" + filter.name() + "%");
    }
    bindCursorParameters(q, cursor, sort);
    q.setMaxResults(size + 1);

    @SuppressWarnings("unchecked")
    List<RestaurantEntity> rows = q.getResultList();
    boolean hasNext = rows.size() > size;
    List<RestaurantEntity> page = hasNext ? rows.subList(0, size) : rows;
    return new CursorPage<>(
        page.stream().map(RestaurantPostgresAdapter::toDomain).toList(), hasNext);
  }

  private static void appendKeysetPredicate(
      StringBuilder sql, RestaurantCursor cursor, RestaurantSort sort) {
    if (cursor == null) {
      return;
    }
    switch (sort) {
      case CREATED_AT_DESC ->
          sql.append("and (r.created_at < :ck or (r.created_at = :ck and r.id < :cid)) ");
      case RATING_DESC -> {
        RestaurantCursor.ByRating c = (RestaurantCursor.ByRating) cursor;
        if (c.avgRating() == null) {
          sql.append("and r.avg_rating is null and r.id < :cid ");
        } else {
          sql.append(
              "and (r.avg_rating < :ck "
                  + "or (r.avg_rating = :ck and r.id < :cid) "
                  + "or r.avg_rating is null) ");
        }
      }
      case NAME_ASC -> sql.append("and (r.name > :ck or (r.name = :ck and r.id > :cid)) ");
    }
  }

  private static void bindCursorParameters(Query q, RestaurantCursor cursor, RestaurantSort sort) {
    if (cursor == null) {
      return;
    }
    switch (sort) {
      case CREATED_AT_DESC -> {
        RestaurantCursor.ByCreatedAt c = (RestaurantCursor.ByCreatedAt) cursor;
        q.setParameter("ck", c.createdAt());
        q.setParameter("cid", c.id());
      }
      case RATING_DESC -> {
        RestaurantCursor.ByRating c = (RestaurantCursor.ByRating) cursor;
        if (c.avgRating() != null) {
          q.setParameter("ck", c.avgRating());
        }
        q.setParameter("cid", c.id());
      }
      case NAME_ASC -> {
        RestaurantCursor.ByName c = (RestaurantCursor.ByName) cursor;
        q.setParameter("ck", c.name());
        q.setParameter("cid", c.id());
      }
    }
  }

  private static String orderByClause(RestaurantSort sort) {
    return switch (sort) {
      case CREATED_AT_DESC -> "order by r.created_at desc, r.id desc";
      case RATING_DESC -> "order by r.avg_rating desc nulls last, r.id desc";
      case NAME_ASC -> "order by r.name asc, r.id asc";
    };
  }

  private static RestaurantEntity toEntity(Restaurant r) {
    Point point =
        GEOMETRY_FACTORY.createPoint(
            new Coordinate(r.location().longitude(), r.location().latitude()));
    point.setSRID(SRID_WGS84);
    return new RestaurantEntity(r.id().value(), r.name(), r.address(), point, r.createdAt());
  }

  private static Restaurant toDomain(RestaurantEntity e) {
    Point point = e.getLocation();
    return new Restaurant(
        new RestaurantId(e.getId()),
        e.getName(),
        e.getAddress(),
        new Coordinates(point.getY(), point.getX()),
        e.getCreatedAt(),
        e.getAvgRating());
  }
}
