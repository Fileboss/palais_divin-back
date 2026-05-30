package fr.lepgu.palaisdivin.backend.shared;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SchemaSmokeIT extends AbstractIntegrationTest {

  @Autowired JdbcClient jdbcClient;

  @Test
  void v1MigrationAppliesPostgisRestaurantTableAndGistIndex() {
    assertThat(
            jdbcClient
                .sql("SELECT 1 FROM pg_extension WHERE extname = 'postgis'")
                .query(Integer.class)
                .optional())
        .as("postgis extension should be installed")
        .isPresent();

    Map<String, String> columns =
        jdbcClient
            .sql(
                """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'restaurant'
                """)
            .query(
                (rs, rowNum) -> Map.entry(rs.getString("column_name"), rs.getString("data_type")))
            .stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(columns)
        .as("restaurant table columns and types")
        .containsEntry("id", "uuid")
        .containsEntry("name", "text")
        .containsEntry("address", "text")
        .containsEntry("location", "USER-DEFINED")
        .containsEntry("created_at", "timestamp with time zone");

    assertThat(
            jdbcClient
                .sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_restaurant_location'")
                .query(String.class)
                .optional())
        .as("GIST index on location should exist")
        .hasValueSatisfying(def -> assertThat(def).contains("USING gist"));

    assertThat(
            jdbcClient
                .sql("SELECT success FROM flyway_schema_history WHERE version = '1'")
                .query(Boolean.class)
                .optional())
        .as("Flyway should have applied V1 successfully")
        .contains(true);
  }

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
