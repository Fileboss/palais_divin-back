package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, RestaurantPostgresAdapter.class})
class RestaurantPostgresAdapterIT {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Autowired RestaurantPostgresAdapter adapter;

  @Test
  void roundTripPreservesAllFieldsAndAxisOrder() {
    RestaurantId id = RestaurantId.newId();
    Restaurant input =
        new Restaurant(
            id,
            "Le Train Bleu",
            "Gare de Lyon",
            new Coordinates(48.8443, 2.3736),
            FIXED_CREATED_AT,
            null);

    Restaurant saved = adapter.save(input);
    Optional<Restaurant> found = adapter.findById(id);

    assertThat(saved).isEqualTo(input);
    assertThat(found).isPresent();
    Restaurant out = found.get();
    assertThat(out.id()).isEqualTo(id);
    assertThat(out.name()).isEqualTo("Le Train Bleu");
    assertThat(out.address()).isEqualTo("Gare de Lyon");
    assertThat(out.location().latitude()).isEqualTo(48.8443);
    assertThat(out.location().longitude()).isEqualTo(2.3736);
    assertThat(out.createdAt()).isEqualTo(FIXED_CREATED_AT);
  }

  @Test
  void findByIdMissingReturnsEmpty() {
    assertThat(adapter.findById(RestaurantId.newId())).isEmpty();
  }

  @Test
  void deleteByIdRemovesRow() {
    RestaurantId id = RestaurantId.newId();
    adapter.save(
        new Restaurant(
            id,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.8536, 2.3795),
            FIXED_CREATED_AT,
            null));
    assertThat(adapter.findById(id)).isPresent();

    adapter.deleteById(id);

    assertThat(adapter.findById(id)).isEmpty();
  }

  @Test
  void nullAddressRoundTrips() {
    RestaurantId id = RestaurantId.newId();
    Restaurant input =
        new Restaurant(
            id, "Septime", null, new Coordinates(48.8536, 2.3795), FIXED_CREATED_AT, null);

    adapter.save(input);

    assertThat(adapter.findById(id))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.address()).isNull());
  }
}
