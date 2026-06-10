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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
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
    Tag input =
        new Tag(id, TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of(), CREATED_AT);

    Tag saved = adapter.save(input);
    Optional<Tag> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Tag out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.category()).isEqualTo(TagCategory.SPECIALTY);
    assertThat(out.slug()).isEqualTo("natural-wine");
    assertThat(out.label()).isEqualTo("Natural wine");
    assertThat(out.labelI18n()).isEmpty();
    assertThat(out.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void empty_labelI18n_is_persisted_as_null_and_read_back_as_empty_map() {
    TagId id = TagId.newId();
    adapter.save(
        new Tag(id, TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of(), CREATED_AT));
    em.flush();
    em.clear();

    Object raw =
        em.createNativeQuery("SELECT label_i18n FROM tag WHERE id = ?1")
            .setParameter(1, id.value())
            .getSingleResult();
    assertThat(raw).isNull();

    Tag readBack = adapter.findById(id).orElseThrow();
    assertThat(readBack.labelI18n()).isEmpty();
  }

  @Test
  void labelI18n_round_trips_through_jsonb_column() {
    TagId id = TagId.newId();
    Map<String, String> i18n = Map.of("en", "Vegan", "es", "Vegano", "de", "Vegan");
    adapter.save(new Tag(id, TagCategory.REGIME, "vegan", "Végétalien", i18n, CREATED_AT));
    em.flush();
    em.clear();

    Tag readBack = adapter.findById(id).orElseThrow();
    assertThat(readBack.labelI18n())
        .containsEntry("en", "Vegan")
        .containsEntry("es", "Vegano")
        .containsEntry("de", "Vegan")
        .hasSize(3);
  }

  @Test
  void findById_missing_returns_empty() {
    assertThat(adapter.findById(TagId.newId())).isEmpty();
  }

  @Test
  void findAll_orders_by_category_then_slug() {
    adapter.save(
        new Tag(TagId.newId(), TagCategory.VENUE_TYPE, "bistro", "Bistrot", Map.of(), CREATED_AT));
    adapter.save(
        new Tag(
            TagId.newId(),
            TagCategory.SPECIALTY,
            "natural-wine",
            "Natural wine",
            Map.of(),
            CREATED_AT));
    adapter.save(
        new Tag(TagId.newId(), TagCategory.SPECIALTY, "burger", "Burger", Map.of(), CREATED_AT));
    adapter.save(
        new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", Map.of(), CREATED_AT));
    adapter.save(
        new Tag(
            TagId.newId(),
            TagCategory.SERVICE_AND_PLACE,
            "paris-11",
            "Paris 11e",
            Map.of(),
            CREATED_AT));

    List<Tag> all = adapter.findAll();

    assertThat(all)
        .extracting(t -> t.category().name() + ":" + t.slug())
        .containsExactly(
            "REGIME:vegan",
            "SERVICE_AND_PLACE:paris-11",
            "SPECIALTY:burger",
            "SPECIALTY:natural-wine",
            "VENUE_TYPE:bistro");
  }

  @Test
  void deleteById_removes_row() {
    TagId id = TagId.newId();
    adapter.save(
        new Tag(id, TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of(), CREATED_AT));

    adapter.deleteById(id);
    em.flush();

    assertThat(adapter.findById(id)).isEmpty();
  }

  @Test
  void deleteById_cascades_to_restaurant_tag_rows() {
    TagId tagId = TagId.newId();
    adapter.save(
        new Tag(
            tagId, TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of(), CREATED_AT));

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
        new Tag(
            TagId.newId(),
            TagCategory.SPECIALTY,
            "natural-wine",
            "Natural wine",
            Map.of(),
            CREATED_AT));

    assertThatThrownBy(
            () -> {
              adapter.save(
                  new Tag(
                      TagId.newId(),
                      TagCategory.REGIME,
                      "natural-wine",
                      "Natural wine bis",
                      Map.of(),
                      CREATED_AT));
              em.flush();
            })
        .hasMessageContaining("uq_tag_slug");
  }
}
