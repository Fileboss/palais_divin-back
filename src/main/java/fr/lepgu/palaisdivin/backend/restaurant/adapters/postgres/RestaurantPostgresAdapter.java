package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantPostgresAdapter implements RestaurantRepositoryPort {

  private static final int SRID_WGS84 = 4326;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  private final RestaurantJpaRepository jpa;

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
  public CursorPage<Restaurant> findAll(RestaurantCursor cursor, int size, List<String> tagSlugs) {
    PageRequest pageable = PageRequest.of(0, size);
    Slice<RestaurantEntity> slice;
    if (tagSlugs.isEmpty()) {
      slice =
          cursor == null
              ? jpa.findFirstPage(pageable)
              : jpa.findAfter(cursor.createdAt(), cursor.id(), pageable);
    } else {
      slice =
          cursor == null
              ? jpa.findFirstPageFilteredByTags(tagSlugs, tagSlugs.size(), pageable)
              : jpa.findAfterFilteredByTags(
                  cursor.createdAt(), cursor.id(), tagSlugs, tagSlugs.size(), pageable);
    }
    return new CursorPage<>(
        slice.getContent().stream().map(RestaurantPostgresAdapter::toDomain).toList(),
        slice.hasNext());
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
