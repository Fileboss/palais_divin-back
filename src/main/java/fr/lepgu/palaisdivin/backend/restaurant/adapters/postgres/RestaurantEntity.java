package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "restaurant")
class RestaurantEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "address")
  private String address;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
  private Point location;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(
      name = "avg_rating",
      insertable = false,
      updatable = false,
      columnDefinition = "numeric(3,2)")
  private Double avgRating;

  @Column(name = "dine_in", nullable = false)
  private boolean dineIn;

  @Column(name = "take_out", nullable = false)
  private boolean takeOut;

  @Column(name = "delivery", nullable = false)
  private boolean delivery;

  protected RestaurantEntity() {}

  RestaurantEntity(
      UUID id,
      String name,
      String address,
      Point location,
      Instant createdAt,
      boolean dineIn,
      boolean takeOut,
      boolean delivery) {
    this.id = id;
    this.name = name;
    this.address = address;
    this.location = location;
    this.createdAt = createdAt;
    this.dineIn = dineIn;
    this.takeOut = takeOut;
    this.delivery = delivery;
  }

  UUID getId() {
    return id;
  }

  String getName() {
    return name;
  }

  String getAddress() {
    return address;
  }

  Point getLocation() {
    return location;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Double getAvgRating() {
    return avgRating;
  }

  boolean isDineIn() {
    return dineIn;
  }

  boolean isTakeOut() {
    return takeOut;
  }

  boolean isDelivery() {
    return delivery;
  }
}
