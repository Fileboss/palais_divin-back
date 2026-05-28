package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.config.ApplicationConfig;
import fr.lepgu.palaisdivin.backend.restaurant.domain.events.RestaurantCreated;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, OutboxPublisherAdapter.class, ApplicationConfig.class})
class OutboxPublisherAdapterIT {

  @Autowired OutboxPublisherAdapter adapter;
  @Autowired JdbcClient jdbcClient;
  @Autowired EntityManager entityManager;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void publishWithActiveTxInsertsRowWithPendingStatusAndJsonPayload() {
    UUID aggregateId = UUID.randomUUID();
    RestaurantCreated payload =
        new RestaurantCreated(
            aggregateId,
            "Le Train Bleu",
            "Gare de Lyon",
            48.8443,
            2.3736,
            Instant.parse("2026-05-27T10:15:30Z"));

    adapter.publish("Restaurant", aggregateId, "RestaurantCreated", payload);
    entityManager.flush();

    String status =
        jdbcClient
            .sql("SELECT status FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(String.class)
            .single();
    assertThat(status).isEqualTo("PENDING");

    Integer retryCount =
        jdbcClient
            .sql("SELECT retry_count FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(Integer.class)
            .single();
    assertThat(retryCount).isZero();

    JsonNode storedPayload =
        jdbcClient
            .sql("SELECT payload::text FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(
                (rs, rowNum) -> {
                  try {
                    return objectMapper.readTree(rs.getString(1));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                })
            .single();
    assertThat(storedPayload.get("id").asText()).isEqualTo(aggregateId.toString());
    assertThat(storedPayload.get("name").asText()).isEqualTo("Le Train Bleu");
    assertThat(storedPayload.get("address").asText()).isEqualTo("Gare de Lyon");
    assertThat(storedPayload.get("latitude").asDouble()).isEqualTo(48.8443);
    assertThat(storedPayload.get("longitude").asDouble()).isEqualTo(2.3736);

    Long pendingProcessedAt =
        jdbcClient
            .sql(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND processed_at IS NULL")
            .param(aggregateId)
            .query(Long.class)
            .single();
    assertThat(pendingProcessedAt).isEqualTo(1L);
  }

  @Test
  void publishWithoutActiveTxThrowsToEnforceMandatoryPropagation() {
    TestTransaction.end();

    assertThatThrownBy(
            () -> adapter.publish("Restaurant", UUID.randomUUID(), "RestaurantCreated", "payload"))
        .isInstanceOf(IllegalTransactionStateException.class);
  }
}
