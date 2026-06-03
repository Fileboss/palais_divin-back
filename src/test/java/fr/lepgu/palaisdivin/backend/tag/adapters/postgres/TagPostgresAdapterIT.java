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
