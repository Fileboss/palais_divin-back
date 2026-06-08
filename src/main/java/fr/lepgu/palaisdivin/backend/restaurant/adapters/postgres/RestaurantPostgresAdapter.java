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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
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
  private static final String DIST_EXPR =
      "ST_Distance(r.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)";

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
    boolean distanceSort = sort == RestaurantSort.DISTANCE_ASC;
    StringBuilder sql = new StringBuilder();
    if (distanceSort) {
      sql.append(
          "select r.id, r.name, r.address, "
              + "ST_X(r.location::geometry) as lng, ST_Y(r.location::geometry) as lat, "
              + "r.created_at, r.avg_rating, "
              + DIST_EXPR
              + " as dist_m "
              + "from restaurant r ");
    } else {
      sql.append("select r.* from restaurant r ");
    }
    if (filter.hasTags()) {
      sql.append("join restaurant_tag rt on rt.restaurant_id = r.id ");
      sql.append("join tag t on rt.tag_id = t.id ");
    }
    sql.append("where 1=1 ");
    if (filter.hasTags()) {
      sql.append("and t.slug in (:slugs) ");
    }
    if (filter.hasName()) {
      sql.append("and r.name ilike :namePattern ");
    }
    if (filter.hasIdsAllowList()) {
      sql.append("and r.id in (:idsAllowList) ");
    }
    appendKeysetPredicate(sql, cursor, sort);
    if (filter.hasTags()) {
      sql.append("group by r.id having count(distinct rt.tag_id) = :slugCount ");
    }
    sql.append(orderByClause(sort));

    Query q =
        distanceSort
            ? em.createNativeQuery(sql.toString())
            : em.createNativeQuery(sql.toString(), RestaurantEntity.class);
    if (filter.hasTags()) {
      q.setParameter("slugs", filter.tagSlugs());
      q.setParameter("slugCount", filter.tagSlugs().size());
    }
    if (filter.hasName()) {
      q.setParameter("namePattern", "%" + filter.name() + "%");
    }
    if (filter.hasIdsAllowList()) {
      q.setParameter(
          "idsAllowList", filter.idsAllowList().stream().map(RestaurantId::value).toList());
    }
    bindCursorParameters(q, cursor, sort);
    bindAnchorParameters(q, filter, sort);
    q.setMaxResults(size + 1);

    List<Restaurant> hydrated;
    if (distanceSort) {
      @SuppressWarnings("unchecked")
      List<Object[]> rows = q.getResultList();
      hydrated = rows.stream().map(RestaurantPostgresAdapter::toDomainWithDistance).toList();
    } else {
      @SuppressWarnings("unchecked")
      List<RestaurantEntity> rows = q.getResultList();
      hydrated = rows.stream().map(RestaurantPostgresAdapter::toDomain).toList();
    }
    boolean hasNext = hydrated.size() > size;
    List<Restaurant> page = hasNext ? hydrated.subList(0, size) : hydrated;
    return new CursorPage<>(page, hasNext);
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
      case DISTANCE_ASC ->
          sql.append("and (" + DIST_EXPR + " > :ck or (" + DIST_EXPR + " = :ck and r.id > :cid)) ");
      case AFFINITY_DESC ->
          throw new IllegalStateException(
              "AFFINITY_DESC paginates via the recommendation graph, not Postgres keyset");
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
      case DISTANCE_ASC -> {
        RestaurantCursor.ByDistance c = (RestaurantCursor.ByDistance) cursor;
        q.setParameter("ck", c.distanceMetres());
        q.setParameter("cid", c.id());
      }
      case AFFINITY_DESC ->
          throw new IllegalStateException(
              "AFFINITY_DESC paginates via the recommendation graph, not Postgres keyset");
    }
  }

  private static void bindAnchorParameters(Query q, RestaurantFilter filter, RestaurantSort sort) {
    if (sort != RestaurantSort.DISTANCE_ASC) {
      return;
    }
    Coordinates anchor = filter.anchor();
    q.setParameter("lng", anchor.longitude());
    q.setParameter("lat", anchor.latitude());
  }

  private static String orderByClause(RestaurantSort sort) {
    return switch (sort) {
      case CREATED_AT_DESC -> "order by r.created_at desc, r.id desc";
      case RATING_DESC -> "order by r.avg_rating desc nulls last, r.id desc";
      case NAME_ASC -> "order by r.name asc, r.id asc";
      case DISTANCE_ASC -> "order by " + DIST_EXPR + " asc, r.id asc";
      case AFFINITY_DESC ->
          throw new IllegalStateException(
              "AFFINITY_DESC paginates via the recommendation graph, not Postgres keyset");
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
        e.getAvgRating(),
        null);
  }

  private static Restaurant toDomainWithDistance(Object[] row) {
    UUID id = (UUID) row[0];
    String name = (String) row[1];
    String address = (String) row[2];
    double lng = ((Number) row[3]).doubleValue();
    double lat = ((Number) row[4]).doubleValue();
    Instant createdAt = toInstant(row[5]);
    BigDecimal avg = (BigDecimal) row[6];
    Double avgRating = avg == null ? null : avg.doubleValue();
    double distM = ((Number) row[7]).doubleValue();
    return new Restaurant(
        new RestaurantId(id),
        name,
        address,
        new Coordinates(lat, lng),
        createdAt,
        avgRating,
        distM);
  }

  private static Instant toInstant(Object raw) {
    return switch (raw) {
      case Instant i -> i;
      case OffsetDateTime odt -> odt.toInstant();
      case java.sql.Timestamp ts -> ts.toInstant();
      default ->
          throw new IllegalStateException("unexpected timestamp type: " + raw.getClass().getName());
    };
  }
}
