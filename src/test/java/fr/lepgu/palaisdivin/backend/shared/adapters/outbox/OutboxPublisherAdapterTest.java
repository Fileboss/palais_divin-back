package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lepgu.palaisdivin.backend.restaurant.domain.events.RestaurantCreated;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherAdapterTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-27T10:00:00Z");

  @Mock private OutboxEventJpaRepository jpa;

  private OutboxPublisherAdapter adapter;
  private final ObjectMapper referenceMapper = new ObjectMapper().findAndRegisterModules();

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    adapter = new OutboxPublisherAdapter(jpa, fixedClock);
  }

  @Test
  void publishSavesEntityWithPendingStatusZeroRetriesAndClockTimestamp() {
    UUID aggregateId = UUID.randomUUID();
    RestaurantCreated payload =
        new RestaurantCreated(aggregateId, "Septime", "80 Rue de Charonne", 48.85, 2.37, FIXED_NOW);
    when(jpa.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.publish("Restaurant", aggregateId, "RestaurantCreated", payload);

    ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
    verify(jpa).save(captor.capture());
    OutboxEventEntity entity = captor.getValue();

    assertThat(entity.getId()).isNotNull();
    assertThat(entity.getAggregateType()).isEqualTo("Restaurant");
    assertThat(entity.getAggregateId()).isEqualTo(aggregateId);
    assertThat(entity.getEventType()).isEqualTo("RestaurantCreated");
    assertThat(entity.getPayload()).isEqualTo(referenceMapper.valueToTree(payload));
    assertThat(entity.getStatus()).isEqualTo("PENDING");
    assertThat(entity.getRetryCount()).isZero();
    assertThat(entity.getLastError()).isNull();
    assertThat(entity.getCreatedAt()).isEqualTo(FIXED_NOW);
    assertThat(entity.getProcessedAt()).isNull();
  }

  @Test
  void publishGeneratesDistinctIdsAcrossCalls() {
    when(jpa.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.publish("Restaurant", UUID.randomUUID(), "RestaurantCreated", null);
    adapter.publish("Restaurant", UUID.randomUUID(), "RestaurantCreated", null);

    ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
    verify(jpa, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).getId())
        .isNotEqualTo(captor.getAllValues().get(1).getId());
  }
}
