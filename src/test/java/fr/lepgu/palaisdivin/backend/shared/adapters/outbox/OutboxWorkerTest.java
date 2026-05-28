package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-28T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private OutboxEventJpaRepository repo;

  private OutboxWorker workerWith(List<Projector> projectors, int batchSize, int maxRetries) {
    return new OutboxWorker(
        repo, projectors, new OutboxWorkerProperties(batchSize, maxRetries), FIXED_CLOCK);
  }

  private OutboxEventEntity pendingRestaurantRow(int currentRetryCount) {
    OutboxEventEntity event =
        new OutboxEventEntity(
            UUID.randomUUID(),
            "Restaurant",
            UUID.randomUUID(),
            "RestaurantCreated",
            MAPPER.createObjectNode().put("name", "Septime"),
            FIXED_NOW.minusSeconds(60));
    for (int i = 0; i < currentRetryCount; i++) {
      event.incrementRetry();
    }
    return event;
  }

  @Test
  void drainBatchProjectsEachPendingRowAndMarksProcessed() {
    RecordingProjector projector = new RecordingProjector("Restaurant");
    OutboxEventEntity a = pendingRestaurantRow(0);
    OutboxEventEntity b = pendingRestaurantRow(0);
    OutboxEventEntity c = pendingRestaurantRow(0);
    when(repo.findPendingForUpdateSkipLocked(any(Limit.class))).thenReturn(List.of(a, b, c));

    workerWith(List.of(projector), 50, 5).drainBatch();

    assertThat(projector.invocations).hasSize(3);
    assertThat(a.getStatus()).isEqualTo("PROCESSED");
    assertThat(a.getProcessedAt()).isEqualTo(FIXED_NOW);
    assertThat(b.getStatus()).isEqualTo("PROCESSED");
    assertThat(c.getStatus()).isEqualTo("PROCESSED");
  }

  @Test
  void drainBatchMarksRowFailedWhenNoProjectorMatchesAggregateType() {
    OutboxEventEntity orphan =
        new OutboxEventEntity(
            UUID.randomUUID(),
            "UnknownAgg",
            UUID.randomUUID(),
            "SomethingHappened",
            MAPPER.createObjectNode(),
            FIXED_NOW);
    when(repo.findPendingForUpdateSkipLocked(any(Limit.class))).thenReturn(List.of(orphan));

    workerWith(List.of(new RecordingProjector("Restaurant")), 50, 5).drainBatch();

    assertThat(orphan.getStatus()).isEqualTo("FAILED");
    assertThat(orphan.getLastError()).contains("No projector").contains("UnknownAgg");
    assertThat(orphan.getProcessedAt()).isNull();
  }

  @Test
  void drainBatchIncrementsRetryAndKeepsPendingOnFirstFailure() {
    OutboxEventEntity event = pendingRestaurantRow(0);
    when(repo.findPendingForUpdateSkipLocked(any(Limit.class))).thenReturn(List.of(event));
    ThrowingProjector projector = new ThrowingProjector("Restaurant", "boom");

    workerWith(List.of(projector), 50, 5).drainBatch();

    assertThat(event.getStatus()).isEqualTo("PENDING");
    assertThat(event.getRetryCount()).isEqualTo(1);
    assertThat(event.getLastError()).isEqualTo("boom");
    assertThat(event.getProcessedAt()).isNull();
  }

  @Test
  void drainBatchMarksFailedWhenRetryCountReachesMaxRetries() {
    OutboxEventEntity event = pendingRestaurantRow(2);
    when(repo.findPendingForUpdateSkipLocked(any(Limit.class))).thenReturn(List.of(event));

    workerWith(List.of(new ThrowingProjector("Restaurant", "boom")), 50, 3).drainBatch();

    assertThat(event.getStatus()).isEqualTo("FAILED");
    assertThat(event.getRetryCount()).isEqualTo(3);
    assertThat(event.getLastError()).isEqualTo("boom");
    assertThat(event.getProcessedAt()).isNull();
  }

  @Test
  void drainBatchUsesBatchSizeFromProperties() {
    when(repo.findPendingForUpdateSkipLocked(any(Limit.class))).thenReturn(List.of());

    workerWith(List.of(new RecordingProjector("Restaurant")), 17, 5).drainBatch();

    ArgumentCaptor<Limit> captor = ArgumentCaptor.forClass(Limit.class);
    verify(repo).findPendingForUpdateSkipLocked(captor.capture());
    assertThat(captor.getValue().max()).isEqualTo(17);
  }

  @Test
  void drainBatchDoesNothingWhenBatchEmpty() {
    when(repo.findPendingForUpdateSkipLocked(any(Limit.class))).thenReturn(List.of());
    RecordingProjector projector = new RecordingProjector("Restaurant");

    workerWith(List.of(projector), 50, 5).drainBatch();

    assertThat(projector.invocations).isEmpty();
    verify(repo, times(1)).findPendingForUpdateSkipLocked(Limit.of(50));
    verify(repo, never()).save(any());
  }

  private static final class RecordingProjector implements Projector {
    private final String aggregateType;
    final List<String> invocations = new ArrayList<>();

    RecordingProjector(String aggregateType) {
      this.aggregateType = aggregateType;
    }

    @Override
    public String aggregateType() {
      return aggregateType;
    }

    @Override
    public void project(String eventType, JsonNode payload) {
      invocations.add(eventType);
    }
  }

  private static final class ThrowingProjector implements Projector {
    private final String aggregateType;
    private final String message;
    final AtomicInteger calls = new AtomicInteger();

    ThrowingProjector(String aggregateType, String message) {
      this.aggregateType = aggregateType;
      this.message = message;
    }

    @Override
    public String aggregateType() {
      return aggregateType;
    }

    @Override
    public void project(String eventType, JsonNode payload) {
      calls.incrementAndGet();
      throw new RuntimeException(message);
    }
  }
}
