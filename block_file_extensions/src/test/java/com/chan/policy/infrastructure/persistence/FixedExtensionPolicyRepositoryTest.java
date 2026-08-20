package com.chan.policy.infrastructure.persistence;

import com.chan.policy.domain.FixedExtension;
import com.chan.policy.domain.FixedExtensionPolicy;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FixedExtensionPolicyRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    FixedExtensionPolicyRepository fixedExtensionPolicyRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void 고정_확장자_7종이_기본_unchecked_상태로_시드된다() {
        // Arrange: V1__init.sql의 seed 데이터가 Flyway로 이미 적용된 상태

        // Act
        List<FixedExtensionPolicy> policies = fixedExtensionPolicyRepository.findAll();

        // Assert
        assertThat(policies)
                .extracting(FixedExtensionPolicy::getExtension)
                .containsExactlyInAnyOrder(
                        Arrays.stream(FixedExtension.values())
                                .map(FixedExtension::value)
                                .toArray(String[]::new)
                );
        assertThat(policies).allMatch(policy -> !policy.isBlocked());
    }

    @Test
    void 고정_확장자의_차단_상태를_변경하면_DB에_반영된다() {
        FixedExtensionPolicy policy = fixedExtensionPolicyRepository.findById(FixedExtension.EXE.value())
                .orElseThrow();

        policy.changeBlocked(true);
        entityManager.flush();
        entityManager.clear();

        FixedExtensionPolicy updated = fixedExtensionPolicyRepository.findById(FixedExtension.EXE.value())
                .orElseThrow();
        assertThat(updated.isBlocked()).isTrue();
    }
}
