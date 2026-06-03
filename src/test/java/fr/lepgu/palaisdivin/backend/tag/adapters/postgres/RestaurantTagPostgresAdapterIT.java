package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({
  TestcontainersConfiguration.class,
  TagPostgresAdapter.class,
  RestaurantTagPostgresAdapter.class
})
class RestaurantTagPostgresAdapterIT {

  private static final Instant CREATED_AT = Instant.parse("2026-06-03T10:00:00Z");

  @Autowired RestaurantTagPostgresAdapter adapter;
  @PersistenceContext EntityManager em;

  private RestaurantId restaurantId;
  private UserId attacherId;
  private TagId foodTagId;
  private TagId regimeTagId;
  private String suffix;

  @BeforeEach
  void seed() {
    em.createNativeQuery("DELETE FROM restaurant_tag").executeUpdate();
    em.createNativeQuery("DELETE FROM tag").executeUpdate();
    em.createNativeQuery("DELETE FROM restaurant").executeUpdate();
    em.createNativeQuery("DELETE FROM app_user").executeUpdate();

    restaurantId = RestaurantId.newId();
    attacherId = UserId.newId();
    foodTagId = TagId.newId();
    regimeTagId = TagId.newId();
    suffix = UUID.randomUUID().toString().substring(0, 8);

    insertRestaurant(restaurantId.value(), "Septime", "80 Rue de Charonne");
    insertUser(
        attacherId.value(),
        "kc-attacher-" + suffix,
        "attacher-" + suffix + "@example.test",
        "Attacher");
    insertTag(foodTagId.value(), "FOOD", "rt-food-" + suffix, "Food tag");
    insertTag(regimeTagId.value(), "REGIME", "rt-regime-" + suffix, "Regime tag");
  }

  @Test
  void save_round_trips() {
    RestaurantTag attachment = new RestaurantTag(restaurantId, foodTagId, attacherId, CREATED_AT);
    RestaurantTag saved = adapter.save(attachment);

    assertThat(saved).isEqualTo(attachment);
    assertThat(adapter.findByRestaurantAndTag(restaurantId, foodTagId)).isPresent();
  }

  @Test
  void findByRestaurantAndTag_returns_empty_when_no_row() {
    assertThat(adapter.findByRestaurantAndTag(restaurantId, foodTagId)).isEmpty();
  }

  @Test
  void delete_returns_true_when_row_existed_false_otherwise() {
    adapter.save(new RestaurantTag(restaurantId, foodTagId, attacherId, CREATED_AT));
    em.flush();

    assertThat(adapter.delete(restaurantId, foodTagId)).isTrue();
    em.flush();
    assertThat(adapter.delete(restaurantId, foodTagId)).isFalse();
  }

  @Test
  void findTagsByRestaurant_orders_by_category_then_slug() {
    TagId foodBurgerId = TagId.newId();
    insertTag(foodBurgerId.value(), "FOOD", "rt-burger-" + suffix, "Burger");

    adapter.save(new RestaurantTag(restaurantId, regimeTagId, attacherId, CREATED_AT));
    adapter.save(new RestaurantTag(restaurantId, foodTagId, attacherId, CREATED_AT));
    adapter.save(new RestaurantTag(restaurantId, foodBurgerId, attacherId, CREATED_AT));
    em.flush();
    em.clear();

    List<Tag> tags = adapter.findTagsByRestaurant(restaurantId);

    assertThat(tags)
        .extracting(t -> t.category().name() + ":" + t.slug())
        .containsExactly(
            "FOOD:rt-burger-" + suffix, "FOOD:rt-food-" + suffix, "REGIME:rt-regime-" + suffix);
  }

  @Test
  void findTagsByRestaurants_groups_by_restaurant_ordered_by_category_then_slug() {
    RestaurantId other = RestaurantId.newId();
    insertRestaurant(other.value(), "Le Train Bleu", "Gare de Lyon");
    TagId foodBurgerId = TagId.newId();
    insertTag(foodBurgerId.value(), "FOOD", "rt-burger-" + suffix, "Burger");

    // restaurant 1 -> burger, food, vegan(regime)
    adapter.save(new RestaurantTag(restaurantId, foodTagId, attacherId, CREATED_AT));
    adapter.save(new RestaurantTag(restaurantId, foodBurgerId, attacherId, CREATED_AT));
    adapter.save(new RestaurantTag(restaurantId, regimeTagId, attacherId, CREATED_AT));
    // restaurant 2 -> regime only
    adapter.save(new RestaurantTag(other, regimeTagId, attacherId, CREATED_AT));
    em.flush();
    em.clear();

    Map<RestaurantId, List<Tag>> grouped =
        adapter.findTagsByRestaurants(List.of(restaurantId, other));

    assertThat(grouped).hasSize(2);
    assertThat(grouped.get(restaurantId))
        .extracting(t -> t.category().name() + ":" + t.slug())
        .containsExactly(
            "FOOD:rt-burger-" + suffix, "FOOD:rt-food-" + suffix, "REGIME:rt-regime-" + suffix);
    assertThat(grouped.get(other))
        .extracting(t -> t.category().name() + ":" + t.slug())
        .containsExactly("REGIME:rt-regime-" + suffix);
  }

  @Test
  void findTagsByRestaurants_empty_input_returns_empty_map() {
    Map<RestaurantId, List<Tag>> grouped = adapter.findTagsByRestaurants(List.of());
    assertThat(grouped).isEmpty();
  }

  @Test
  void findTagsByRestaurants_unknown_id_returns_empty_map() {
    Map<RestaurantId, List<Tag>> grouped =
        adapter.findTagsByRestaurants(List.of(RestaurantId.newId()));
    assertThat(grouped).isEmpty();
  }

  private void insertRestaurant(UUID id, String name, String address) {
    em.createNativeQuery(
            "INSERT INTO restaurant(id, name, address, location, created_at)"
                + " VALUES (:id, :name, :address,"
                + " ST_GeographyFromText('SRID=4326;POINT(2.38 48.85)'), :createdAt)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("address", address)
        .setParameter("createdAt", CREATED_AT)
        .executeUpdate();
  }

  private void insertUser(UUID id, String subject, String email, String displayName) {
    em.createNativeQuery(
            "INSERT INTO app_user(id, subject, email, display_name, created_at)"
                + " VALUES (:id, :subject, :email, :displayName, :createdAt)")
        .setParameter("id", id)
        .setParameter("subject", subject)
        .setParameter("email", email)
        .setParameter("displayName", displayName)
        .setParameter("createdAt", CREATED_AT)
        .executeUpdate();
  }

  private void insertTag(UUID id, String category, String slug, String label) {
    em.createNativeQuery(
            "INSERT INTO tag(id, category, slug, label, created_at)"
                + " VALUES (:id, :category, :slug, :label, :createdAt)")
        .setParameter("id", id)
        .setParameter("category", category)
        .setParameter("slug", slug)
        .setParameter("label", label)
        .setParameter("createdAt", CREATED_AT)
        .executeUpdate();
  }
}
