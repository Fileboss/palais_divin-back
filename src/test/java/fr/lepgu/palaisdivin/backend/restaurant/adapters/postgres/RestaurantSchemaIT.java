package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

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
class RestaurantSchemaIT {

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
}
