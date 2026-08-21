package com.chan.policy.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PolicyWriteLockMigrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void V3에서_커스텀_확장자_한도용_락_행을_생성한다() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        // "현재 최신 버전"이 아니라 "V3이 적용됐는지"를 확인한다 — 이후 다른 마이그레이션이
        // 추가돼도(V4 등) 이 테스트가 매번 깨지지 않도록 특정 버전의 존재 여부만 검증한다.
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("3");

        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT lock_name
                     FROM policy_write_lock
                     WHERE lock_name = 'CUSTOM_EXTENSION_LIMIT'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("lock_name")).isEqualTo("CUSTOM_EXTENSION_LIMIT");
            assertThat(resultSet.next()).isFalse();
        }
    }
}
