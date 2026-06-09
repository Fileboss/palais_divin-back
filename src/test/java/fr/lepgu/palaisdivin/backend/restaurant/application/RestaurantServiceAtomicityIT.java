package fr.lepgu.palaisdivin.backend.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import fr.lepgu.palaisdivin.backend.SharedTestStubs.BanApiClientStub;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RestaurantServiceAtomicityIT extends AbstractIntegrationTest {

  @Autowired RestaurantService service;
  @Autowired JdbcClient jdbcClient;
  @Autowired PlatformTransactionManager txManager;
  @Autowired BanApiClientStub banApiClient;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
    banApiClient.reset();
  }

  @AfterEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM idempotency_key").update();
    jdbcClient.sql("DELETE FROM review").update();
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
  }

  @Test
  void createPersistsBothRestaurantAndOutboxRowInSameTx() {
    Restaurant created = service.create("Septime", "80 Rue de Charonne", true, false, false);

    Long restaurantCount =
        jdbcClient
            .sql("SELECT count(*) FROM restaurant WHERE id = ?")
            .param(created.id().value())
            .query(Long.class)
            .single();
    assertThat(restaurantCount).isEqualTo(1L);

    Long outboxCount =
        jdbcClient
            .sql(
                """
                SELECT count(*) FROM outbox_event
                WHERE aggregate_id = ?
                  AND aggregate_type = 'Restaurant'
                  AND event_type = 'RestaurantCreated'
                  AND status = 'PENDING'
                """)
            .param(created.id().value())
            .query(Long.class)
            .single();
    assertThat(outboxCount).isEqualTo(1L);

    String payloadName =
        jdbcClient
            .sql("SELECT payload->>'name' FROM outbox_event WHERE aggregate_id = ?")
            .param(created.id().value())
            .query(String.class)
            .single();
    assertThat(payloadName).isEqualTo("Septime");
  }

  @Test
  void rollbackAfterPublishLeavesBothTablesEmpty() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(txManager)
                    .executeWithoutResult(
                        status -> {
                          service.create("Septime", "80 Rue de Charonne", true, false, false);
                          throw new RuntimeException("force rollback");
                        }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("force rollback");

    Long restaurantCount =
        jdbcClient.sql("SELECT count(*) FROM restaurant").query(Long.class).single();
    assertThat(restaurantCount).isZero();

    Long outboxCount =
        jdbcClient.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    assertThat(outboxCount).isZero();
  }
}
