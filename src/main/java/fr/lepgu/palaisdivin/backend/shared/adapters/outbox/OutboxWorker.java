package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(Projector.class)
public class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
  private static final int MAX_ERROR_LENGTH = 1000;
  private static final String PROJECTION_TIMER = "palaisdivin.outbox.projection";

  private final OutboxEventJpaRepository repo;
  private final Map<String, Projector> byAggregateType;
  private final OutboxWorkerProperties props;
  private final Clock clock;
  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, Timer> projectionTimers = new ConcurrentHashMap<>();

  public OutboxWorker(
      OutboxEventJpaRepository repo,
      List<Projector> projectors,
      OutboxWorkerProperties props,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repo = repo;
    this.byAggregateType =
        projectors.stream().collect(Collectors.toUnmodifiableMap(Projector::aggregateType, p -> p));
    this.props = props;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
    Gauge.builder("palaisdivin.outbox.lag", this, OutboxWorker::oldestPendingAgeSeconds)
        .description("Age in seconds of the oldest PENDING outbox event")
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  @Transactional
  public void drainBatch() {
    List<OutboxEventEntity> batch =
        repo.findPendingForUpdateSkipLocked(Limit.of(props.batchSize()));
    for (OutboxEventEntity event : batch) {
      processOne(event);
    }
  }

  private void processOne(OutboxEventEntity event) {
    Projector projector = byAggregateType.get(event.getAggregateType());
    if (projector == null) {
      event.markFailed("No projector registered for aggregateType=" + event.getAggregateType());
      return;
    }
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      projector.project(event.getEventType(), event.getPayload());
      sample.stop(projectionTimer(event.getAggregateType(), "success"));
      event.markProcessed(Instant.now(clock));
    } catch (RuntimeException ex) {
      sample.stop(projectionTimer(event.getAggregateType(), "failure"));
      event.incrementRetry();
      String msg = truncate(ex.getMessage());
      if (event.getRetryCount() >= props.maxRetries()) {
        event.markFailed(msg);
      } else {
        event.recordError(msg);
      }
      log.warn(
          "Projection failed for event {} (retry {}/{})",
          event.getId(),
          event.getRetryCount(),
          props.maxRetries(),
          ex);
    }
  }

  private Timer projectionTimer(String aggregateType, String outcome) {
    return projectionTimers.computeIfAbsent(
        aggregateType + "|" + outcome,
        key ->
            Timer.builder(PROJECTION_TIMER)
                .description("Latency of Neo4j projection per outbox event")
                .tags(Tags.of("aggregate_type", aggregateType, "outcome", outcome))
                .register(meterRegistry));
  }

  @Transactional(readOnly = true)
  protected double oldestPendingAgeSeconds() {
    return repo.findOldestPendingCreatedAt()
        .map(oldest -> Duration.between(oldest, Instant.now(clock)).toMillis() / 1000.0)
        .orElse(0.0);
  }

  private static String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
  }
}
