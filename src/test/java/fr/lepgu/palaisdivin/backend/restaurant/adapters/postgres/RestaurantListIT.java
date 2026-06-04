package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
@Import({TestcontainersConfiguration.class, RestaurantPostgresAdapter.class})
class RestaurantListIT {

  @Autowired RestaurantPostgresAdapter adapter;
  @PersistenceContext EntityManager em;

  @BeforeEach
  void resetState() {
    em.createNativeQuery("DELETE FROM restaurant_tag").executeUpdate();
    em.createNativeQuery("DELETE FROM tag").executeUpdate();
    em.createNativeQuery("DELETE FROM restaurant").executeUpdate();
  }

  @Test
  void walkAllPagesByCursor_descendingByCreatedAtThenId_noOverlapNoSkip() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    Set<UUID> inserted = new HashSet<>();
    for (int i = 0; i < 25; i++) {
      RestaurantId id = RestaurantId.newId();
      inserted.add(id.value());
      adapter.save(
          new Restaurant(
              id, "r-" + i, null, new Coordinates(48.8536, 2.3795), base.plusSeconds(i), null));
    }

    List<Restaurant> collected = new ArrayList<>();
    RestaurantCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Restaurant> page =
          adapter.findAll(cursor, 10, RestaurantFilter.none(), RestaurantSort.CREATED_AT_DESC);
      collected.addAll(page.data());
      pages++;
      if (!page.hasNext()) {
        break;
      }
      Restaurant last = page.data().getLast();
      cursor = new RestaurantCursor.ByCreatedAt(last.createdAt(), last.id().value());
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
              Instant.parse("2026-05-27T10:00:00Z").plusSeconds(i),
              null));
    }
    CursorPage<Restaurant> page =
        adapter.findAll(null, 10, RestaurantFilter.none(), RestaurantSort.CREATED_AT_DESC);
    assertThat(page.data()).hasSize(3);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void findAll_singleTagFilter_returnsOnlyRestaurantsCarryingThatTag() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId rA = saveRestaurant("A", base);
    RestaurantId rB = saveRestaurant("B", base.plusSeconds(1));
    RestaurantId rC = saveRestaurant("C", base.plusSeconds(2));

    UUID attacher = seedUser(suffix);
    UUID burger = seedTag("FOOD", "rl-burger-" + suffix, "Burger");
    UUID vegan = seedTag("REGIME", "rl-vegan-" + suffix, "Vegan");
    attach(rA.value(), burger, attacher, base);
    attach(rC.value(), burger, attacher, base);
    attach(rB.value(), vegan, attacher, base);

    CursorPage<Restaurant> page =
        adapter.findAll(
            null,
            10,
            new RestaurantFilter(List.of("rl-burger-" + suffix), null),
            RestaurantSort.CREATED_AT_DESC);

    assertThat(page.data().stream().map(r -> r.id().value()))
        .containsExactlyInAnyOrder(rA.value(), rC.value());
  }

  @Test
  void findAll_twoTagFilter_requiresAllOfThem() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId rA = saveRestaurant("A", base);
    RestaurantId rB = saveRestaurant("B", base.plusSeconds(1));
    RestaurantId rC = saveRestaurant("C", base.plusSeconds(2));

    UUID attacher = seedUser(suffix);
    UUID burger = seedTag("FOOD", "rl-burger-" + suffix, "Burger");
    UUID vegan = seedTag("REGIME", "rl-vegan-" + suffix, "Vegan");
    attach(rA.value(), burger, attacher, base);
    attach(rA.value(), vegan, attacher, base);
    attach(rB.value(), burger, attacher, base);
    attach(rC.value(), vegan, attacher, base);

    CursorPage<Restaurant> page =
        adapter.findAll(
            null,
            10,
            new RestaurantFilter(List.of("rl-burger-" + suffix, "rl-vegan-" + suffix), null),
            RestaurantSort.CREATED_AT_DESC);

    assertThat(page.data().stream().map(r -> r.id().value())).containsExactly(rA.value());
  }

  @Test
  void findAll_unknownSlug_returnsEmptyPage() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    saveRestaurant("A", base);
    saveRestaurant("B", base.plusSeconds(1));

    CursorPage<Restaurant> page =
        adapter.findAll(
            null,
            10,
            new RestaurantFilter(List.of("nonexistent-slug-" + UUID.randomUUID()), null),
            RestaurantSort.CREATED_AT_DESC);

    assertThat(page.data()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void findAll_filteredCursorWalk_paginatesWithoutDuplicates() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    UUID attacher = seedUser(suffix);
    UUID burger = seedTag("FOOD", "rl-burger-" + suffix, "Burger");

    Set<UUID> attached = new HashSet<>();
    for (int i = 0; i < 7; i++) {
      RestaurantId rid = saveRestaurant("burger-" + i, base.plusSeconds(i));
      attach(rid.value(), burger, attacher, base);
      attached.add(rid.value());
    }
    // also seed an untagged restaurant we should never see
    saveRestaurant("untagged", base.plusSeconds(100));

    List<UUID> collected = new ArrayList<>();
    RestaurantCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Restaurant> page =
          adapter.findAll(
              cursor,
              3,
              new RestaurantFilter(List.of("rl-burger-" + suffix), null),
              RestaurantSort.CREATED_AT_DESC);
      page.data().forEach(r -> collected.add(r.id().value()));
      pages++;
      if (!page.hasNext()) break;
      Restaurant last = page.data().getLast();
      cursor = new RestaurantCursor.ByCreatedAt(last.createdAt(), last.id().value());
      if (pages > 5) throw new AssertionError("paging did not terminate");
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).isEqualTo(attached);
  }

  @Test
  void findAll_nameFilter_caseInsensitive_returnsMatches() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId bistrot = saveRestaurant("Le Bistrot", base);
    saveRestaurant("Le Train Bleu", base.plusSeconds(1));

    CursorPage<Restaurant> lower =
        adapter.findAll(
            null, 10, new RestaurantFilter(List.of(), "bistrot"), RestaurantSort.CREATED_AT_DESC);
    CursorPage<Restaurant> upper =
        adapter.findAll(
            null, 10, new RestaurantFilter(List.of(), "BISTROT"), RestaurantSort.CREATED_AT_DESC);

    assertThat(lower.data().stream().map(r -> r.id().value())).containsExactly(bistrot.value());
    assertThat(upper.data().stream().map(r -> r.id().value())).containsExactly(bistrot.value());
  }

  @Test
  void findAll_nameAndTagFilter_ands() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId burgerBistro = saveRestaurant("Burger Bistro", base);
    RestaurantId burgerHouse = saveRestaurant("Burger House", base.plusSeconds(1));
    RestaurantId regularBistro = saveRestaurant("Regular Bistro", base.plusSeconds(2));

    UUID attacher = seedUser(suffix);
    UUID burger = seedTag("FOOD", "rl-burger-" + suffix, "Burger");
    attach(burgerBistro.value(), burger, attacher, base);
    attach(burgerHouse.value(), burger, attacher, base);
    // regularBistro tagged not-burger
    UUID vegan = seedTag("REGIME", "rl-vegan-" + suffix, "Vegan");
    attach(regularBistro.value(), vegan, attacher, base);

    CursorPage<Restaurant> page =
        adapter.findAll(
            null,
            10,
            new RestaurantFilter(List.of("rl-burger-" + suffix), "bistro"),
            RestaurantSort.CREATED_AT_DESC);

    assertThat(page.data().stream().map(r -> r.id().value())).containsExactly(burgerBistro.value());
  }

  @Test
  void findAll_nameOnly_paginatesAcrossCursor() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    Set<UUID> matching = new HashSet<>();
    for (int i = 0; i < 7; i++) {
      RestaurantId rid = saveRestaurant("Bistro " + i, base.plusSeconds(i));
      matching.add(rid.value());
    }
    saveRestaurant("Cafe Decoy", base.plusSeconds(100));

    List<UUID> collected = new ArrayList<>();
    RestaurantCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Restaurant> page =
          adapter.findAll(
              cursor, 3, new RestaurantFilter(List.of(), "bistro"), RestaurantSort.CREATED_AT_DESC);
      page.data().forEach(r -> collected.add(r.id().value()));
      pages++;
      if (!page.hasNext()) break;
      Restaurant last = page.data().getLast();
      cursor = new RestaurantCursor.ByCreatedAt(last.createdAt(), last.id().value());
      if (pages > 5) throw new AssertionError("paging did not terminate");
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).isEqualTo(matching);
  }

  @Test
  void findAll_sortByRatingDesc_ratedFirst_nullsLast() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId r1 = saveRestaurant("R1", base);
    RestaurantId r2 = saveRestaurant("R2", base.plusSeconds(1));
    RestaurantId r3 = saveRestaurant("R3", base.plusSeconds(2));
    RestaurantId r4 = saveRestaurant("R4", base.plusSeconds(3));
    RestaurantId r5 = saveRestaurant("R5", base.plusSeconds(4));
    setRating(r1.value(), "4.50");
    setRating(r2.value(), "3.00");
    setRating(r4.value(), "4.50");
    // r3 and r5 remain NULL

    CursorPage<Restaurant> page =
        adapter.findAll(null, 10, RestaurantFilter.none(), RestaurantSort.RATING_DESC);

    List<UUID> ids = page.data().stream().map(r -> r.id().value()).toList();
    // first two are 4.50-rated (r1, r4), in some order by id-desc tiebreaker
    assertThat(ids.subList(0, 2)).containsExactlyInAnyOrder(r1.value(), r4.value());
    assertThat(ids.get(2)).isEqualTo(r2.value());
    assertThat(ids.subList(3, 5)).containsExactlyInAnyOrder(r3.value(), r5.value());
  }

  @Test
  void findAll_sortByRatingDesc_keysetPagesThroughNullBoundary_noOverlap() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    Set<UUID> all = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      RestaurantId rid = saveRestaurant("rated-" + i, base.plusSeconds(i));
      setRating(rid.value(), String.valueOf(4.0 - i * 0.5)); // 4.0, 3.5, 3.0, 2.5
      all.add(rid.value());
    }
    for (int i = 0; i < 3; i++) {
      RestaurantId rid = saveRestaurant("unrated-" + i, base.plusSeconds(10 + i));
      all.add(rid.value());
    }

    List<UUID> collected = new ArrayList<>();
    RestaurantCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Restaurant> page =
          adapter.findAll(cursor, 2, RestaurantFilter.none(), RestaurantSort.RATING_DESC);
      page.data().forEach(r -> collected.add(r.id().value()));
      pages++;
      if (!page.hasNext()) break;
      Restaurant last = page.data().getLast();
      java.math.BigDecimal rating =
          last.avgRating() == null ? null : java.math.BigDecimal.valueOf(last.avgRating());
      cursor = new RestaurantCursor.ByRating(rating, last.id().value());
      if (pages > 10) throw new AssertionError("paging did not terminate");
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).isEqualTo(all);
  }

  @Test
  void findAll_sortByRatingDesc_combinedWithTagFilter_filtersThenSorts() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId withTagHi = saveRestaurant("WithTagHi", base);
    RestaurantId withTagLo = saveRestaurant("WithTagLo", base.plusSeconds(1));
    RestaurantId noTagHi = saveRestaurant("NoTagHi", base.plusSeconds(2));
    setRating(withTagHi.value(), "4.50");
    setRating(withTagLo.value(), "2.00");
    setRating(noTagHi.value(), "5.00");

    UUID attacher = seedUser(suffix);
    UUID burger = seedTag("FOOD", "rl-burger-" + suffix, "Burger");
    attach(withTagHi.value(), burger, attacher, base);
    attach(withTagLo.value(), burger, attacher, base);

    CursorPage<Restaurant> page =
        adapter.findAll(
            null,
            10,
            new RestaurantFilter(List.of("rl-burger-" + suffix), null),
            RestaurantSort.RATING_DESC);

    assertThat(page.data().stream().map(r -> r.id().value()))
        .containsExactly(withTagHi.value(), withTagLo.value());
  }

  @Test
  void findAll_sortByNameAsc_alphabetical() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId carmen = saveRestaurant("Carmen", base);
    RestaurantId allard = saveRestaurant("Allard", base.plusSeconds(1));
    RestaurantId benoit = saveRestaurant("Benoit", base.plusSeconds(2));

    CursorPage<Restaurant> page =
        adapter.findAll(null, 10, RestaurantFilter.none(), RestaurantSort.NAME_ASC);

    assertThat(page.data().stream().map(r -> r.id().value()))
        .containsExactly(allard.value(), benoit.value(), carmen.value());
  }

  @Test
  void findAll_sortByNameAsc_keysetPaginates_noOverlap() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    Set<UUID> all = new HashSet<>();
    String[] names = {"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf"};
    for (int i = 0; i < names.length; i++) {
      RestaurantId rid = saveRestaurant(names[i], base.plusSeconds(i));
      all.add(rid.value());
    }

    List<UUID> collected = new ArrayList<>();
    RestaurantCursor cursor = null;
    int pages = 0;
    while (true) {
      CursorPage<Restaurant> page =
          adapter.findAll(cursor, 3, RestaurantFilter.none(), RestaurantSort.NAME_ASC);
      page.data().forEach(r -> collected.add(r.id().value()));
      pages++;
      if (!page.hasNext()) break;
      Restaurant last = page.data().getLast();
      cursor = new RestaurantCursor.ByName(last.name(), last.id().value());
      if (pages > 5) throw new AssertionError("paging did not terminate");
    }

    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).isEqualTo(all);
  }

  @Test
  void findAll_sortByNameAsc_combinedWithNameFilter_filtersThenSorts() {
    Instant base = Instant.parse("2026-05-27T10:00:00Z");
    RestaurantId bistroC = saveRestaurant("Bistro Carmen", base);
    RestaurantId bistroA = saveRestaurant("Bistro Allard", base.plusSeconds(1));
    saveRestaurant("Cafe Decoy", base.plusSeconds(2));

    CursorPage<Restaurant> page =
        adapter.findAll(
            null, 10, new RestaurantFilter(List.of(), "bistro"), RestaurantSort.NAME_ASC);

    assertThat(page.data().stream().map(r -> r.id().value()))
        .containsExactly(bistroA.value(), bistroC.value());
  }

  private void setRating(UUID restaurantId, String rating) {
    em.createNativeQuery("UPDATE restaurant SET avg_rating = :r WHERE id = :id")
        .setParameter("r", new java.math.BigDecimal(rating))
        .setParameter("id", restaurantId)
        .executeUpdate();
    em.flush();
    em.clear();
  }

  private RestaurantId saveRestaurant(String name, Instant createdAt) {
    RestaurantId id = RestaurantId.newId();
    adapter.save(new Restaurant(id, name, null, new Coordinates(48.8536, 2.3795), createdAt, null));
    return id;
  }

  private UUID seedUser(String suffix) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO app_user(id, subject, email, display_name, created_at)"
                + " VALUES (:id, :subject, :email, :displayName, now())")
        .setParameter("id", id)
        .setParameter("subject", "kc-rlist-" + suffix)
        .setParameter("email", "rlist-" + suffix + "@example.test")
        .setParameter("displayName", "RList")
        .executeUpdate();
    return id;
  }

  private UUID seedTag(String category, String slug, String label) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO tag(id, category, slug, label, created_at)"
                + " VALUES (:id, :category, :slug, :label, now())")
        .setParameter("id", id)
        .setParameter("category", category)
        .setParameter("slug", slug)
        .setParameter("label", label)
        .executeUpdate();
    return id;
  }

  private void attach(UUID restaurantId, UUID tagId, UUID attachedBy, Instant attachedAt) {
    em.createNativeQuery(
            "INSERT INTO restaurant_tag(restaurant_id, tag_id, attached_by, attached_at)"
                + " VALUES (:r, :t, :u, :at)")
        .setParameter("r", restaurantId)
        .setParameter("t", tagId)
        .setParameter("u", attachedBy)
        .setParameter("at", attachedAt)
        .executeUpdate();
  }
}
