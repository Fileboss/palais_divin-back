package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class TagPostgresAdapterIT {

  private static final Instant CREATED_AT = Instant.parse("2026-06-03T10:00:00Z");

  @Autowired TagPostgresAdapter adapter;
  @PersistenceContext EntityManager em;

  @BeforeEach
  void cleanTags() {
    em.createNativeQuery("DELETE FROM restaurant_tag").executeUpdate();
    em.createNativeQuery("DELETE FROM tag").executeUpdate();
  }

  @Test
  void save_round_trips_through_postgres() {
    TagId id = TagId.newId();
    Tag input = new Tag(id, TagCategory.FOOD, "natural-wine", "Natural wine", CREATED_AT);

    Tag saved = adapter.save(input);
    Optional<Tag> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Tag out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.category()).isEqualTo(TagCategory.FOOD);
    assertThat(out.slug()).isEqualTo("natural-wine");
    assertThat(out.label()).isEqualTo("Natural wine");
    assertThat(out.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void findById_missing_returns_empty() {
    assertThat(adapter.findById(TagId.newId())).isEmpty();
  }

  @Test
  void findAll_orders_by_category_then_slug() {
    adapter.save(new Tag(TagId.newId(), TagCategory.VENUE_TYPE, "bistro", "Bistrot", CREATED_AT));
    adapter.save(
        new Tag(TagId.newId(), TagCategory.FOOD, "natural-wine", "Natural wine", CREATED_AT));
    adapter.save(new Tag(TagId.newId(), TagCategory.FOOD, "burger", "Burger", CREATED_AT));
    adapter.save(new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", CREATED_AT));
    adapter.save(new Tag(TagId.newId(), TagCategory.PLACE, "paris-11", "Paris 11e", CREATED_AT));

    List<Tag> all = adapter.findAll();

    assertThat(all)
        .extracting(t -> t.category().name() + ":" + t.slug())
        .containsExactly(
            "FOOD:burger",
            "FOOD:natural-wine",
            "PLACE:paris-11",
            "REGIME:vegan",
            "VENUE_TYPE:bistro");
  }

  @Test
  void deleteById_removes_row() {
    TagId id = TagId.newId();
    adapter.save(new Tag(id, TagCategory.FOOD, "natural-wine", "Natural wine", CREATED_AT));

    adapter.deleteById(id);
    em.flush();

    assertThat(adapter.findById(id)).isEmpty();
  }

  @Test
  void deleteById_cascades_to_restaurant_tag_rows() {
    TagId tagId = TagId.newId();
    adapter.save(new Tag(tagId, TagCategory.FOOD, "natural-wine", "Natural wine", CREATED_AT));

    java.util.UUID restaurantId = java.util.UUID.randomUUID();
    java.util.UUID userId = java.util.UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO restaurant (id, name, address, location, created_at)"
                + " VALUES (?1, ?2, ?3, ST_SetSRID(ST_MakePoint(?4, ?5), 4326)::geography, ?6)")
        .setParameter(1, restaurantId)
        .setParameter(2, "Septime")
        .setParameter(3, "80 Rue de Charonne")
        .setParameter(4, 2.38)
        .setParameter(5, 48.85)
        .setParameter(6, CREATED_AT)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO app_user (id, subject, email, display_name, created_at)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, userId)
        .setParameter(2, "subject-" + userId)
        .setParameter(3, userId + "@test.local")
        .setParameter(4, "Test User")
        .setParameter(5, CREATED_AT)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO restaurant_tag (restaurant_id, tag_id, attached_by, attached_at)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, restaurantId)
        .setParameter(2, tagId.value())
        .setParameter(3, userId)
        .setParameter(4, CREATED_AT)
        .executeUpdate();
    em.flush();
    em.clear();

    adapter.deleteById(tagId);
    em.flush();

    Long remaining =
        ((Number)
                em.createNativeQuery("SELECT count(*) FROM restaurant_tag WHERE tag_id = ?1")
                    .setParameter(1, tagId.value())
                    .getSingleResult())
            .longValue();
    assertThat(remaining).isZero();
  }

  @Test
  void unique_slug_is_enforced() {
    adapter.save(
        new Tag(TagId.newId(), TagCategory.FOOD, "natural-wine", "Natural wine", CREATED_AT));

    assertThatThrownBy(
            () -> {
              adapter.save(
                  new Tag(
                      TagId.newId(),
                      TagCategory.REGIME,
                      "natural-wine",
                      "Natural wine bis",
                      CREATED_AT));
              em.flush();
            })
        .hasMessageContaining("uq_tag_slug");
  }
}
