package fr.lepgu.palaisdivin.backend.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.config.MinioProperties;
import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoCursor;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummary;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoSummaryPage;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoRepositoryPort;
import fr.lepgu.palaisdivin.backend.photo.domain.ports.PhotoStoragePort;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final URI URL_A = URI.create("https://minio.test/a?sig=1");
  private static final URI URL_B = URI.create("https://minio.test/b?sig=2");

  @Mock PhotoRepositoryPort photos;
  @Mock PhotoStoragePort storage;

  PhotoQueryService service;
  MinioProperties properties;

  @BeforeEach
  void setUp() {
    properties =
        new MinioProperties(
            "http://minio.test",
            "k",
            "s",
            Duration.ofSeconds(2),
            "palaisdivin-photos",
            Duration.ofMinutes(10),
            TTL);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new PhotoQueryService(photos, storage, properties, clock);
  }

  @Test
  void loadReturnsThumbnailPerRestaurant() {
    RestaurantId r1 = RestaurantId.newId();
    RestaurantId r2 = RestaurantId.newId();
    Photo p1 = photo(r1, "k1", NOW.minusSeconds(60));
    Photo p2 = photo(r2, "k2", NOW.minusSeconds(30));
    when(photos.findOldestByRestaurantIds(List.of(r1, r2)))
        .thenReturn(Map.of(r1, p1, r2, p2));
    when(storage.presignGet(eq("k1"), eq(TTL))).thenReturn(URL_A);
    when(storage.presignGet(eq("k2"), eq(TTL))).thenReturn(URL_B);

    Map<RestaurantId, PhotoSummary> result = service.load(List.of(r1, r2));

    assertThat(result).hasSize(2);
    assertThat(result.get(r1).url()).isEqualTo(URL_A);
    assertThat(result.get(r1).expiresAt()).isEqualTo(NOW.plus(TTL));
    assertThat(result.get(r2).url()).isEqualTo(URL_B);
  }

  @Test
  void loadEmptyShortCircuits() {
    assertThat(service.load(List.of())).isEmpty();
  }

  @Test
  void listReturnsPageAndNextCursorWhenMore() {
    RestaurantId rid = RestaurantId.newId();
    Photo p1 = photo(rid, "k1", NOW.minusSeconds(120));
    Photo p2 = photo(rid, "k2", NOW.minusSeconds(60));
    when(photos.findByRestaurantId(eq(rid), any(), eq(2)))
        .thenReturn(new CursorPage<>(List.of(p1, p2), true));
    when(storage.presignGet(eq("k1"), eq(TTL))).thenReturn(URL_A);
    when(storage.presignGet(eq("k2"), eq(TTL))).thenReturn(URL_B);

    PhotoSummaryPage page = service.list(rid, null, 2);

    assertThat(page.data()).hasSize(2);
    assertThat(page.hasNext()).isTrue();
    assertThat(page.nextCursor()).isNotNull();
    assertThat(page.nextCursor().createdAt()).isEqualTo(p2.createdAt());
    assertThat(page.nextCursor().id()).isEqualTo(p2.id().value());
  }

  @Test
  void listOmitsNextCursorWhenNoMore() {
    RestaurantId rid = RestaurantId.newId();
    Photo p1 = photo(rid, "k1", NOW.minusSeconds(60));
    when(photos.findByRestaurantId(eq(rid), eq(null), eq(20)))
        .thenReturn(new CursorPage<>(List.of(p1), false));
    when(storage.presignGet(eq("k1"), eq(TTL))).thenReturn(URL_A);

    PhotoSummaryPage page = service.list(rid, null, 20);

    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void listForwardsCursor() {
    RestaurantId rid = RestaurantId.newId();
    PhotoCursor cursor = new PhotoCursor(NOW.minusSeconds(3600), java.util.UUID.randomUUID());
    when(photos.findByRestaurantId(rid, cursor, 20))
        .thenReturn(new CursorPage<>(List.of(), false));

    PhotoSummaryPage page = service.list(rid, cursor, 20);

    assertThat(page.data()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }

  private static Photo photo(RestaurantId restaurantId, String objectKey, Instant createdAt) {
    return new Photo(
        PhotoId.newId(), restaurantId, UserId.newId(), objectKey, "image/jpeg", createdAt);
  }
}
