package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

class RestaurantTagPk implements Serializable {

  private UUID restaurantId;
  private UUID tagId;

  public RestaurantTagPk() {}

  RestaurantTagPk(UUID restaurantId, UUID tagId) {
    this.restaurantId = restaurantId;
    this.tagId = tagId;
  }

  public UUID getRestaurantId() {
    return restaurantId;
  }

  public UUID getTagId() {
    return tagId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RestaurantTagPk that)) return false;
    return Objects.equals(restaurantId, that.restaurantId) && Objects.equals(tagId, that.tagId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(restaurantId, tagId);
  }
}
