package fr.lepgu.palaisdivin.backend.shared.adapters.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OutboxEventSchemaIT {

  @Autowired JdbcClient jdbcClient;

  @Test
  void v2MigrationAppliesOutboxEventTableIndexAndStatusCheck() {
    Map<String, String> columns =
        jdbcClient
            .sql(
                """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'outbox_event'
                """)
            .query(
                (rs, rowNum) -> Map.entry(rs.getString("column_name"), rs.getString("data_type")))
            .stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(columns)
        .as("outbox_event table columns and types")
        .containsEntry("id", "uuid")
        .containsEntry("aggregate_type", "text")
        .containsEntry("aggregate_id", "uuid")
        .containsEntry("event_type", "text")
        .containsEntry("payload", "jsonb")
        .containsEntry("status", "text")
        .containsEntry("retry_count", "integer")
        .containsEntry("last_error", "text")
        .containsEntry("created_at", "timestamp with time zone")
        .containsEntry("processed_at", "timestamp with time zone");

    assertThat(
            jdbcClient
                .sql(
                    "SELECT indexdef FROM pg_indexes WHERE indexname ="
                        + " 'idx_outbox_event_status_created_at'")
                .query(String.class)
                .optional())
        .as("composite index on (status, created_at) should exist")
        .hasValueSatisfying(def -> assertThat(def).contains("status").contains("created_at"));

    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT pg_get_constraintdef(c.oid)
                    FROM pg_constraint c
                    JOIN pg_class t ON t.oid = c.conrelid
                    WHERE t.relname = 'outbox_event' AND c.contype = 'c'
                    """)
                .query(String.class)
                .list())
        .as("CHECK constraint should restrict status to PENDING/PROCESSED/FAILED")
        .anySatisfy(
            def ->
                assertThat(def)
                    .contains("status")
                    .contains("PENDING")
                    .contains("PROCESSED")
                    .contains("FAILED"));

    assertThat(
            jdbcClient
                .sql("SELECT success FROM flyway_schema_history WHERE version = '2'")
                .query(Boolean.class)
                .optional())
        .as("Flyway should have applied V2 successfully")
        .contains(true);
  }
}
