package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.task.scheduling.enabled=false",
      "palaisdivin.outbox.batch-size=5",
      "palaisdivin.outbox.max-retries=3"
    })
@Import({TestcontainersConfiguration.class, OutboxWorkerIT.TestProjectors.class})
class OutboxWorkerIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired OutboxEventJpaRepository repo;
  @Autowired OutboxWorkerProperties props;
  @Autowired Clock clock;
  @Autowired BlockingProjector blockingProjector;
  @Autowired AlwaysFailingProjector failingProjector;
  @Autowired JdbcClient jdbcClient;
  @Autowired PlatformTransactionManager txManager;

  @BeforeEach
  void cleanState() {
    jdbcClient.sql("DELETE FROM outbox_event").update();
    blockingProjector.reset();
    failingProjector.reset();
  }

  @AfterEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM outbox_event").update();
  }

  @Test
  void twoConcurrentWorkersEachProcessRowsExactlyOnceUnderSkipLocked() throws Exception {
    insertPendingRestaurantRows(10);

    List<Projector> projectors = List.of(blockingProjector, failingProjector);
    OutboxWorker w1 = new OutboxWorker(repo, projectors, props, clock);
    OutboxWorker w2 = new OutboxWorker(repo, projectors, props, clock);
    TransactionTemplate tx = new TransactionTemplate(txManager);

    CountDownLatch startGate = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      pool.submit(() -> runUnderTx(startGate, tx, w1));
      pool.submit(() -> runUnderTx(startGate, tx, w2));
      startGate.countDown();
      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(blockingProjector.projectedIds).hasSize(10);
    assertThat(new HashSet<>(blockingProjector.projectedIds)).hasSize(10);
    assertThat(new HashSet<>(blockingProjector.projectingThreads))
        .as("Both worker threads should have done some work (SKIP LOCKED race)")
        .hasSizeGreaterThanOrEqualTo(2);

    Long processed =
        jdbcClient
            .sql("SELECT count(*) FROM outbox_event WHERE status = 'PROCESSED'")
            .query(Long.class)
            .single();
    assertThat(processed).isEqualTo(10L);

    Long stillOpen =
        jdbcClient
            .sql("SELECT count(*) FROM outbox_event WHERE status <> 'PROCESSED'")
            .query(Long.class)
            .single();
    assertThat(stillOpen).isZero();

    Long unprocessedTimestamps =
        jdbcClient
            .sql("SELECT count(*) FROM outbox_event WHERE processed_at IS NULL")
            .query(Long.class)
            .single();
    assertThat(unprocessedTimestamps).isZero();
  }

  @Test
  void repeatedFailureMarksRowFailedAfterMaxRetries() {
    UUID aggregateId = UUID.randomUUID();
    insertPendingRow(aggregateId, "FailingAgg", "BoomEvent");

    OutboxWorker worker =
        new OutboxWorker(repo, List.of(blockingProjector, failingProjector), props, clock);
    TransactionTemplate tx = new TransactionTemplate(txManager);

    for (int i = 0; i < props.maxRetries(); i++) {
      tx.executeWithoutResult(s -> worker.drainBatch());
    }

    String status =
        jdbcClient
            .sql("SELECT status FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(String.class)
            .single();
    Integer retryCount =
        jdbcClient
            .sql("SELECT retry_count FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(Integer.class)
            .single();
    String lastError =
        jdbcClient
            .sql("SELECT last_error FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(String.class)
            .single();

    assertThat(status).isEqualTo("FAILED");
    assertThat(retryCount).isEqualTo(props.maxRetries());
    assertThat(lastError).isEqualTo("boom");
    assertThat(failingProjector.callCount.get()).isEqualTo(props.maxRetries());
  }

  @Test
  void noOpWhenNoProjectorMatchesAggregateType() {
    UUID aggregateId = UUID.randomUUID();
    insertPendingRow(aggregateId, "Unhandled", "Whatever");

    OutboxWorker worker =
        new OutboxWorker(repo, List.of(blockingProjector, failingProjector), props, clock);
    new TransactionTemplate(txManager).executeWithoutResult(s -> worker.drainBatch());

    String status =
        jdbcClient
            .sql("SELECT status FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(String.class)
            .single();
    String lastError =
        jdbcClient
            .sql("SELECT last_error FROM outbox_event WHERE aggregate_id = ?")
            .param(aggregateId)
            .query(String.class)
            .single();

    assertThat(status).isEqualTo("FAILED");
    assertThat(lastError).contains("No projector").contains("Unhandled");
  }

  private void runUnderTx(CountDownLatch gate, TransactionTemplate tx, OutboxWorker worker) {
    try {
      gate.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    tx.executeWithoutResult(s -> worker.drainBatch());
  }

  private void insertPendingRestaurantRows(int count) {
    for (int i = 0; i < count; i++) {
      insertPendingRow(UUID.randomUUID(), "Restaurant", "RestaurantCreated");
    }
  }

  private void insertPendingRow(UUID aggregateId, String aggregateType, String eventType) {
    String payload = "{\"id\":\"" + aggregateId + "\",\"name\":\"row-" + aggregateId + "\"}";
    jdbcClient
        .sql(
            """
            INSERT INTO outbox_event
              (id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', 0, now())
            """)
        .param(UUID.randomUUID())
        .param(aggregateType)
        .param(aggregateId)
        .param(eventType)
        .param(payload)
        .update();
  }

  @TestConfiguration
  static class TestProjectors {

    @Bean
    BlockingProjector blockingProjector() {
      return new BlockingProjector();
    }

    @Bean
    AlwaysFailingProjector alwaysFailingProjector() {
      return new AlwaysFailingProjector();
    }
  }

  static final class BlockingProjector implements Projector {
    final ConcurrentLinkedQueue<String> projectedIds = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<String> projectingThreads = new ConcurrentLinkedQueue<>();

    @Override
    public String aggregateType() {
      return "Restaurant";
    }

    @Override
    public void project(String eventType, JsonNode payload) {
      projectingThreads.add(Thread.currentThread().getName());
      projectedIds.add(payload.get("id").asText());
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    void reset() {
      projectedIds.clear();
      projectingThreads.clear();
    }
  }

  static final class AlwaysFailingProjector implements Projector {
    final java.util.concurrent.atomic.AtomicInteger callCount =
        new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public String aggregateType() {
      return "FailingAgg";
    }

    @Override
    public void project(String eventType, JsonNode payload) {
      callCount.incrementAndGet();
      throw new RuntimeException("boom");
    }

    void reset() {
      callCount.set(0);
    }
  }
}
