package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, RestaurantPostgresAdapter.class})
class RestaurantListIT {

  @Autowired RestaurantPostgresAdapter adapter;

  @Test
  void walkAllPagesByCursor_descendingByCreatedAtThenId_noOverlapNoSkip() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    Set<UUID> inserted = new HashSet<>();
    for (int i = 0; i < 25; i++) {
      RestaurantId id = RestaurantId.newId();
      inserted.add(id.value());
      adapter.save(
          new Restaurant(
              id, "r-" + i, null, new Coordinates(48.8536, 2.3795), base.plusSeconds(i)));
    }

    List<Restaurant> collected = new ArrayList<>();
    RestaurantCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Restaurant> page = adapter.findAll(cursor, 10);
      collected.addAll(page.data());
      pages++;
      if (!page.hasNext()) {
        break;
      }
      Restaurant last = page.data().getLast();
      cursor = new RestaurantCursor(last.createdAt(), last.id().value());
      if (pages > 10) {
        throw new AssertionError("paging did not terminate");
      }
    }

    assertThat(pages).isEqualTo(3);
    assertThat(collected).hasSize(25);
    assertThat(collected.stream().map(r -> r.id().value()).toList()).doesNotHaveDuplicates();
    assertThat(collected.stream().map(r -> r.id().value()).toList())
        .containsExactlyInAnyOrderElementsOf(inserted);

    for (int i = 1; i < collected.size(); i++) {
      Restaurant prev = collected.get(i - 1);
      Restaurant curr = collected.get(i);
      assertThat(prev.createdAt()).isAfterOrEqualTo(curr.createdAt());
      if (prev.createdAt().equals(curr.createdAt())) {
        assertThat(prev.id().value().compareTo(curr.id().value())).isPositive();
      }
    }
  }

  @Test
  void firstPage_sizeLargerThanData_hasNextFalse() {
    for (int i = 0; i < 3; i++) {
      adapter.save(
          new Restaurant(
              RestaurantId.newId(),
              "r-" + i,
              null,
              new Coordinates(48.8536, 2.3795),
              Instant.parse("2026-05-27T10:00:00Z").plusSeconds(i)));
    }
    CursorPage<Restaurant> page = adapter.findAll(null, 10);
    assertThat(page.data()).hasSize(3);
    assertThat(page.hasNext()).isFalse();
  }
}
