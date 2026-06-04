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

class PublicUserReviewsRestIT extends AbstractIntegrationTest {

  @LocalServerPort int port;
  @Autowired RestaurantRepositoryPort restaurants;
  @Autowired UserRepositoryPort users;
  @Autowired ReviewRepositoryPort reviews;
  @Autowired JdbcClient jdbcClient;

  private UserId authorId;

  @BeforeEach
  void cleanAndSeed() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant_tag").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    jdbcClient.sql("DELETE FROM user_connection").update();
    jdbcClient.sql("DELETE FROM app_user").update();

    User author =
        users.save(
            new User(
                UserId.newId(),
                "subj-author-" + UUID.randomUUID(),
                "author-" + UUID.randomUUID() + "@example.test",
                "Author",
                Instant.now()));
    authorId = author.id();
  }

  @Test
  void list_returns200WithReviewsForAuthor_orderedByCreatedAtDesc() {
    Instant base = Instant.parse("2026-06-04T10:00:00Z");
    Restaurant rA = saveRestaurant("Septime", "80 Rue de Charonne");
    Restaurant rB = saveRestaurant("Le Train Bleu", "Gare de Lyon");
    reviews.save(new Review(ReviewId.newId(), rA.id(), authorId, 5, "Top", base.plusSeconds(20)));
    reviews.save(new Review(ReviewId.newId(), rB.id(), authorId, 3, "Meh", base.plusSeconds(10)));

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    AuthorReviewsPageResponse body =
        unauthed
            .get()
            .uri("/api/v1/public/users/{uid}/reviews", authorId.value())
            .retrieve()
            .body(AuthorReviewsPageResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.data()).hasSize(2);
    assertThat(body.data().get(0).rating()).isEqualTo(5);
    assertThat(body.data().get(0).restaurant().name()).isEqualTo("Septime");
    assertThat(body.data().get(0).restaurant().address()).isEqualTo("80 Rue de Charonne");
    assertThat(body.data().get(0).restaurant().id()).isEqualTo(rA.id().value());
    assertThat(body.data().get(1).rating()).isEqualTo(3);
    assertThat(body.data().get(1).restaurant().name()).isEqualTo("Le Train Bleu");
    assertThat(body.page().hasNext()).isFalse();
    assertThat(body.page().nextCursor()).isNull();
  }

  @Test
  void list_returnsEmptyPage_whenAuthorHasNoReviews() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    AuthorReviewsPageResponse body =
        unauthed
            .get()
            .uri("/api/v1/public/users/{uid}/reviews", authorId.value())
            .retrieve()
            .body(AuthorReviewsPageResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.data()).isEmpty();
    assertThat(body.page().hasNext()).isFalse();
    assertThat(body.page().nextCursor()).isNull();
  }

  @Test
  void list_returnsEmptyPage_whenAuthorUnknown() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    AuthorReviewsPageResponse body =
        unauthed
            .get()
            .uri("/api/v1/public/users/{uid}/reviews", UUID.randomUUID())
            .retrieve()
            .body(AuthorReviewsPageResponse.class);

    assertThat(body).isNotNull();
    assertThat(body.data()).isEmpty();
    assertThat(body.page().hasNext()).isFalse();
    assertThat(body.page().nextCursor()).isNull();
  }

  @Test
  void list_walksAllPagesViaCursor_noDuplicatesNoSkips() {
    Instant base = Instant.parse("2026-06-04T10:00:00Z");
    Set<UUID> seededIds = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      Restaurant rest = saveRestaurant("R-" + i, "addr-" + i);
      Review saved =
          reviews.save(
              new Review(
                  ReviewId.newId(),
                  rest.id(),
                  authorId,
                  ((i % 5) + 1),
                  "c-" + i,
                  base.plusSeconds(i * 10L)));
      seededIds.add(saved.id().value());
    }

    RestClient unauthed = RestClient.create("http://localhost:" + port);
    List<UUID> collected = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    while (true) {
      String path =
          cursor == null
              ? "/api/v1/public/users/" + authorId.value() + "/reviews?size=2"
              : "/api/v1/public/users/" + authorId.value() + "/reviews?size=2&cursor=" + cursor;
      AuthorReviewsPageResponse body =
          unauthed.get().uri(path).retrieve().body(AuthorReviewsPageResponse.class);
      assertThat(body).isNotNull();
      body.data().forEach(r -> collected.add(r.reviewId()));
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
  void list_invalidCursor_returns400_problemDetail() {
    RestClient unauthed = RestClient.create("http://localhost:" + port);
    ResponseEntity<String> resp =
        unauthed
            .get()
            .uri("/api/v1/public/users/{uid}/reviews?cursor=not!base64!!", authorId.value())
            .retrieve()
            .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
            .toEntity(String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(resp.getBody()).contains("/problems/invalid-cursor");
  }

  private Restaurant saveRestaurant(String name, String address) {
    return restaurants.save(
        new Restaurant(
            RestaurantId.newId(),
            name,
            address,
            new Coordinates(48.8536, 2.3795),
            Instant.now(),
            null));
  }
}
