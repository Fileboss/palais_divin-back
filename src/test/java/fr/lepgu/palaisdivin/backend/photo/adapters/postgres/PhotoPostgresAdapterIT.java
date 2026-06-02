package fr.lepgu.palaisdivin.backend.photo.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.photo.domain.model.Photo;
import fr.lepgu.palaisdivin.backend.photo.domain.model.PhotoId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
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
@Import({TestcontainersConfiguration.class, PhotoPostgresAdapter.class})
class PhotoPostgresAdapterIT {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-06-02T10:00:00Z");

  private static final UUID RESTAURANT_UUID = UUID.randomUUID();
  private static final UUID AUTHOR_UUID = UUID.randomUUID();

  private static final RestaurantId RESTAURANT_ID = new RestaurantId(RESTAURANT_UUID);
  private static final UserId AUTHOR_ID = new UserId(AUTHOR_UUID);

  @Autowired PhotoPostgresAdapter adapter;
  @PersistenceContext EntityManager em;

  @BeforeEach
  void seedFkTargets() {
    em.createNativeQuery(
            "INSERT INTO restaurant (id, name, location, created_at)"
                + " VALUES (?, ?, ST_GeographyFromText('SRID=4326;POINT(2.35 48.85)'), now())")
        .setParameter(1, RESTAURANT_UUID)
        .setParameter(2, "Le Train Bleu")
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO app_user (id, subject, email, display_name) VALUES (?, ?, ?, ?)")
        .setParameter(1, AUTHOR_UUID)
        .setParameter(2, "subj-photo-a")
        .setParameter(3, "a@example.com")
        .setParameter(4, "Author A")
        .executeUpdate();
  }

  @Test
  void roundTripPreservesAllFields() {
    PhotoId id = PhotoId.newId();
    String objectKey = "restaurants/" + RESTAURANT_UUID + "/" + UUID.randomUUID();
    Photo input =
        new Photo(id, RESTAURANT_ID, AUTHOR_ID, objectKey, "image/jpeg", FIXED_CREATED_AT);

    Photo saved = adapter.save(input);
    Optional<Photo> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Photo out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.restaurantId()).isEqualTo(RESTAURANT_ID);
    assertThat(out.authorId()).isEqualTo(AUTHOR_ID);
    assertThat(out.objectKey()).isEqualTo(objectKey);
    assertThat(out.contentType()).isEqualTo("image/jpeg");
    assertThat(out.createdAt()).isEqualTo(FIXED_CREATED_AT);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(adapter.findById(PhotoId.newId())).isEmpty();
  }

  @Test
  void uniqueObjectKeyIsEnforced() {
    String sharedKey = "restaurants/" + RESTAURANT_UUID + "/" + UUID.randomUUID();
    adapter.save(
        new Photo(
            PhotoId.newId(), RESTAURANT_ID, AUTHOR_ID, sharedKey, "image/jpeg", FIXED_CREATED_AT));

    assertThatThrownBy(
            () -> {
              adapter.save(
                  new Photo(
                      PhotoId.newId(),
                      RESTAURANT_ID,
                      AUTHOR_ID,
                      sharedKey,
                      "image/png",
                      FIXED_CREATED_AT));
              em.flush();
            })
        .hasMessageContaining("uq_photo_object_key");
  }
}
