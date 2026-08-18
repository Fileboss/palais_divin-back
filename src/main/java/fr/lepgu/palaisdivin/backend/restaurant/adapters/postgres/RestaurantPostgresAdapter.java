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
  static final String DIST_EXPR =
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
    RestaurantSortStrategy strategy = RestaurantSortStrategy.forSort(sort);
    boolean distanceSort = sort == RestaurantSort.DISTANCE_ASC;
    StringBuilder sql = new StringBuilder();
    if (distanceSort) {
      sql.append(
          "select r.id, r.name, r.address, "
              + "ST_X(r.location::geometry) as lng, ST_Y(r.location::geometry) as lat, "
              + "r.created_at, r.avg_rating, "
              + "r.dine_in, r.take_out, r.delivery, "
              + DIST_EXPR
              + " as dist_m "
              + "from restaurant r ");
    } else {
      sql.append("select r.* from restaurant r ");
    }
    sql.append("where 1=1 ");
    if (filter.hasTags()) {
      for (int i = 0; i < filter.tagSlugGroups().size(); i++) {
        sql.append(
            "and exists (select 1 from restaurant_tag rt"
                + i
                + " join tag t"
                + i
                + " on rt"
                + i
                + ".tag_id = t"
                + i
                + ".id where rt"
                + i
                + ".restaurant_id = r.id and t"
                + i
                + ".slug in (:slugs"
                + i
                + ")) ");
      }
    }
    if (filter.hasName()) {
      sql.append("and r.name ilike :namePattern ");
    }
    if (filter.hasIdsAllowList()) {
      sql.append("and r.id in (:idsAllowList) ");
    }
    if (filter.hasDineIn()) {
      sql.append("and r.dine_in = :dineIn ");
    }
    if (filter.hasTakeOut()) {
      sql.append("and r.take_out = :takeOut ");
    }
    if (filter.hasDelivery()) {
      sql.append("and r.delivery = :delivery ");
    }
    if (cursor != null) {
      sql.append(strategy.keysetPredicate(cursor));
    }
    sql.append(strategy.orderByClause());

    Query q =
        distanceSort
            ? em.createNativeQuery(sql.toString())
            : em.createNativeQuery(sql.toString(), RestaurantEntity.class);
    if (filter.hasTags()) {
      for (int i = 0; i < filter.tagSlugGroups().size(); i++) {
        q.setParameter("slugs" + i, filter.tagSlugGroups().get(i));
      }
    }
    if (filter.hasName()) {
      q.setParameter("namePattern", "%" + filter.name() + "%");
    }
    if (filter.hasIdsAllowList()) {
      q.setParameter(
          "idsAllowList", filter.idsAllowList().stream().map(RestaurantId::value).toList());
    }
    if (filter.hasDineIn()) {
      q.setParameter("dineIn", filter.dineIn());
    }
    if (filter.hasTakeOut()) {
      q.setParameter("takeOut", filter.takeOut());
    }
    if (filter.hasDelivery()) {
      q.setParameter("delivery", filter.delivery());
    }
    if (cursor != null) {
      strategy.bindCursorParameters(q, cursor);
    }
    strategy.bindAnchorParameters(q, filter);
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

  private static RestaurantEntity toEntity(Restaurant r) {
    Point point =
        GEOMETRY_FACTORY.createPoint(
            new Coordinate(r.location().longitude(), r.location().latitude()));
    point.setSRID(SRID_WGS84);
    return new RestaurantEntity(
        r.id().value(),
        r.name(),
        r.address(),
        point,
        r.createdAt(),
        r.dineIn(),
        r.takeOut(),
        r.delivery());
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
        null,
        null,
        e.isDineIn(),
        e.isTakeOut(),
        e.isDelivery());
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
    boolean dineIn = (Boolean) row[7];
    boolean takeOut = (Boolean) row[8];
    boolean delivery = (Boolean) row[9];
    double distM = ((Number) row[10]).doubleValue();
    return new Restaurant(
        new RestaurantId(id),
        name,
        address,
        new Coordinates(lat, lng),
        createdAt,
        avgRating,
        distM,
        null,
        dineIn,
        takeOut,
        delivery);
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
