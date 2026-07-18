package com.beachape.aminam.integration.infra;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class SchemaMigrationTest {

  @Inject DataSource dataSource;

  @Test
  void principalsTableExistsAfterFlywayMigration() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT count(*) FROM principals")) {
      // Querying the table at all proves the Flyway V1 migration ran against Dev Services Postgres.
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isNotNegative();
    }
  }
}
