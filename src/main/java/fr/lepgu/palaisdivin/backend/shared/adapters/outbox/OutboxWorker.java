package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(Projector.class)
class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final OutboxEventJpaRepository repo;
  private final Map<String, Projector> byAggregateType;
  private final OutboxWorkerProperties props;
  private final Clock clock;

  OutboxWorker(
      OutboxEventJpaRepository repo,
      List<Projector> projectors,
      OutboxWorkerProperties props,
      Clock clock) {
    this.repo = repo;
    this.byAggregateType =
        projectors.stream().collect(Collectors.toUnmodifiableMap(Projector::aggregateType, p -> p));
    this.props = props;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${palaisdivin.outbox.poll-delay-ms:1000}")
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
    try {
      projector.project(event.getEventType(), event.getPayload());
      event.markProcessed(Instant.now(clock));
    } catch (RuntimeException ex) {
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

  private static String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
  }
}
