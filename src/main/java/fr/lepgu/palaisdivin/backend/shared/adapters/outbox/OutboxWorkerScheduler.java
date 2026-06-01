package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(OutboxWorker.class)
@ConditionalOnProperty(
    name = "palaisdivin.outbox.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxWorkerScheduler {

  private final OutboxWorker worker;

  OutboxWorkerScheduler(OutboxWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${palaisdivin.outbox.poll-delay-ms:1000}")
  void tick() {
    worker.drainBatch();
  }
}
