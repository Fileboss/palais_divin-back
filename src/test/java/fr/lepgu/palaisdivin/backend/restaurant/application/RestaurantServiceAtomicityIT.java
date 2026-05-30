package fr.lepgu.palaisdivin.backend.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.GeocoderPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import(TestcontainersConfiguration.class)
class RestaurantServiceAtomicityIT {

  private static final Coordinates STUB_LOCATION = new Coordinates(48.8536, 2.3795);

  @Autowired RestaurantService service;
  @Autowired JdbcClient jdbcClient;
  @Autowired PlatformTransactionManager txManager;

  @MockitoBean GeocoderPort geocoder;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
  }

  @AfterEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM outbox_event").update();
    jdbcClient.sql("DELETE FROM restaurant").update();
  }

  @Test
  void createPersistsBothRestaurantAndOutboxRowInSameTx() {
    when(geocoder.geocode(any())).thenReturn(STUB_LOCATION);

    Restaurant created = service.create("Septime", "80 Rue de Charonne");

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
    when(geocoder.geocode(any())).thenReturn(STUB_LOCATION);

    assertThatThrownBy(
            () ->
                new TransactionTemplate(txManager)
                    .executeWithoutResult(
                        status -> {
                          service.create("Septime", "80 Rue de Charonne");
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
