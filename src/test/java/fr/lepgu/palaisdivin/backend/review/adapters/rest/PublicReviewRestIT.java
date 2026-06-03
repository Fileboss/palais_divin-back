package fr.lepgu.palaisdivin.backend.review.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.review.domain.model.Review;
import fr.lepgu.palaisdivin.backend.review.domain.model.ReviewId;
import fr.lepgu.palaisdivin.backend.review.domain.ports.ReviewRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class PublicReviewRestIT extends AbstractIntegrationTest {

  @LocalServerPort int port;
  @Autowired RestaurantRepositoryPort restaurants;
  @Autowired UserRepositoryPort users;
  @Autowired ReviewRepositoryPort reviews;
  @Autowired JdbcClient jdbcClient;

  private RestaurantId restaurantId;

  @BeforeEach
  void cleanAndSeed() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    jdbcClient.sql("DELETE FROM user_connection").update();
    jdbcClient.sql("DELETE FROM app_user").update();

    Restaurant r =
        restaurants.save(
            new Restaurant(
                RestaurantId.newId(),
                "Septime",
                "80 Rue de Charonne",
                new Coordinates(48.8536, 2.3795),
                Instant.now(),
                null));
    restaurantId = r.id();
  }

  @Test
  void list_isReachableWithoutAuth_andReturnsSeededReviews() {
    seedReviews(3);

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ReviewsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants/{rid}/reviews?size=100", restaurantId.value())
            .retrieve()
            .body(ReviewsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data()).hasSize(3);
    assertThat(page.page().size()).isEqualTo(100);
    assertThat(page.page().hasNext()).isFalse();
    assertThat(page.page().nextCursor()).isNull();
  }

  @Test
  void list_walks_all_pages_by_cursor_withoutAuth() {
    Set<UUID> seededIds = seedReviews(7);

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/restaurants/" + restaurantId.value() + "/reviews?size=3"
              : "/api/v1/public/restaurants/"
                  + restaurantId.value()
                  + "/reviews?size=3&cursor="
                  + cursor;
      ReviewsPageResponse body =
          unauthed.get().uri(path).retrieve().body(ReviewsPageResponse.class);
      assertThat(body).isNotNull();
      body.data().forEach(r -> collected.add(r.id()));
      pages++;
      if (!body.page().hasNext()) break;
      cursor = body.page().nextCursor();
      assertThat(cursor).isNotBlank();
      if (pages > 10) throw new AssertionError("paging did not terminate");
    }

    assertThat(pages).isEqualTo(3);
    assertThat(collected).doesNotHaveDuplicates();
    assertThat(new HashSet<>(collected)).isEqualTo(seededIds);
  }

  @Test
  void list_invalidCursor_returns400_problemDetail_withoutAuth() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri(
                "/api/v1/public/restaurants/{rid}/reviews?cursor=not!base64!!",
                restaurantId.value())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/invalid-cursor");
  }

  @Test
  void list_unknownRestaurant_returnsEmptyPage() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ReviewsPageResponse page =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants/{rid}/reviews", UUID.randomUUID())
            .retrieve()
            .body(ReviewsPageResponse.class);

    assertThat(page).isNotNull();
    assertThat(page.data()).isEmpty();
    assertThat(page.page().hasNext()).isFalse();
    assertThat(page.page().nextCursor()).isNull();
  }

  @Test
  void getByAuthor_found_returns200_withBody() {
    User u =
        users.save(
            new User(
                UserId.newId(),
                "subj-author-" + UUID.randomUUID(),
                "author@example.test",
                "Author",
                Instant.now()));
    reviews.save(
        new Review(
            ReviewId.newId(),
            restaurantId,
            u.id(),
            5,
            "Superb",
            Instant.parse("2026-05-31T10:00:00Z")));

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ReviewResponse body =
        unauthed
            .get()
            .uri(
                "/api/v1/public/restaurants/{rid}/reviews/author/{aid}",
                restaurantId.value(),
                u.id().value())
            .retrieve()
            .body(ReviewResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.restaurantId()).isEqualTo(restaurantId.value());
    assertThat(body.rating()).isEqualTo(5);
    assertThat(body.comment()).isEqualTo("Superb");
  }

  @Test
  void list_includesAuthorDisplayName() {
    User alice =
        users.save(
            new User(
                UserId.newId(),
                "subj-alice-" + UUID.randomUUID(),
                "alice-" + UUID.randomUUID() + "@example.test",
                "Alice",
                Instant.now()));
    reviews.save(
        new Review(
            ReviewId.newId(),
            restaurantId,
            alice.id(),
            5,
            "Excellent",
            Instant.parse("2026-05-31T10:00:00Z")));

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ReviewsPageResponse body =
        unauthed
            .get()
            .uri("/api/v1/public/restaurants/{rid}/reviews", restaurantId.value())
            .retrieve()
            .body(ReviewsPageResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.data()).hasSize(1);
    assertThat(body.data().get(0).authorDisplayName()).isEqualTo("Alice");
    assertThat(body.data().get(0).authorId()).isEqualTo(alice.id().value());
  }

  @Test
  void getByAuthor_includesAuthorDisplayName() {
    User author =
        users.save(
            new User(
                UserId.newId(),
                "subj-named-" + UUID.randomUUID(),
                "named-" + UUID.randomUUID() + "@example.test",
                "Charlie",
                Instant.now()));
    reviews.save(
        new Review(
            ReviewId.newId(),
            restaurantId,
            author.id(),
            5,
            "Top",
            Instant.parse("2026-05-31T10:00:00Z")));

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ReviewResponse body =
        unauthed
            .get()
            .uri(
                "/api/v1/public/restaurants/{rid}/reviews/author/{aid}",
                restaurantId.value(),
                author.id().value())
            .retrieve()
            .body(ReviewResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.authorDisplayName()).isEqualTo("Charlie");
  }

  @Test
  void getByAuthor_notFound_returns404_problemDetail() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri(
                "/api/v1/public/restaurants/{rid}/reviews/author/{aid}",
                restaurantId.value(),
                UUID.randomUUID())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/not-found");
  }

  private Set<UUID> seedReviews(int count) {
    Set<UUID> ids = new HashSet<>();
    Instant base = Instant.parse("2026-05-31T10:00:00Z");
    for (int i = 0; i < count; i++) {
      User u =
          users.save(
              new User(
                  UserId.newId(),
                  "subj-" + UUID.randomUUID(),
                  "u" + i + "-" + UUID.randomUUID() + "@example.test",
                  "User " + i,
                  base));
      Review saved =
          reviews.save(
              new Review(
                  ReviewId.newId(),
                  restaurantId,
                  u.id(),
                  ((i % 5) + 1),
                  "comment-" + i,
                  base.plusSeconds(i * 10L)));
      ids.add(saved.id().value());
    }
    return ids;
  }
}
