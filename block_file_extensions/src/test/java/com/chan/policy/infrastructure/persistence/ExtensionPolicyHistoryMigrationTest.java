package com.chan.policy.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ExtensionPolicyHistoryMigrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void V1_기존_DB의_데이터를_유지하면서_V2_정책_변경_이력_테이블을_생성한다() throws Exception {
        Flyway flywayV1 = flyway(MigrationVersion.fromVersion("1"));
        flywayV1.migrate();

        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO custom_extension (extension) VALUES ('pdf')");
        }

        Flyway flywayV2 = flyway(null);
        flywayV2.migrate();

        assertThat(flywayV2.info().current().getVersion().getVersion()).isEqualTo("2");

        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryInt(statement,
                    "SELECT COUNT(*) FROM custom_extension WHERE extension = 'pdf'"))
                    .isEqualTo(1);

            statement.executeUpdate("""
                    INSERT INTO extension_policy_history (policy_type, extension, action)
                    VALUES ('CUSTOM', 'pdf', 'ADD')
                    """);

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT policy_type, extension, action, changed_at
                    FROM extension_policy_history
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("policy_type")).isEqualTo("CUSTOM");
                assertThat(resultSet.getString("extension")).isEqualTo("pdf");
                assertThat(resultSet.getString("action")).isEqualTo("ADD");
                assertThat(resultSet.getTimestamp("changed_at")).isNotNull();
            }

            assertThat(queryInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'extension_policy_history'
                      AND index_name = 'idx_history_extension'
                    """))
                    .isEqualTo(1);
        }
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration");

        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private int queryInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}
