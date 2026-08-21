package com.chan.policy.infrastructure.persistence;

import com.chan.policy.domain.ExtensionPolicyAction;
import com.chan.policy.domain.ExtensionPolicyHistory;
import com.chan.policy.domain.ExtensionPolicyType;
import com.chan.policy.repository.ExtensionPolicyHistoryRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExtensionPolicyHistoryRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    ExtensionPolicyHistoryRepository extensionPolicyHistoryRepository;

    @Test
    void 정책_변경_이력을_저장하면_enum과_변경_시각이_함께_기록된다() {
        ExtensionPolicyHistory saved = extensionPolicyHistoryRepository.saveAndFlush(
                ExtensionPolicyHistory.fixedPolicyChanged("exe", true)
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPolicyType()).isEqualTo(ExtensionPolicyType.FIXED);
        assertThat(saved.getExtension()).isEqualTo("exe");
        assertThat(saved.getAction()).isEqualTo(ExtensionPolicyAction.BLOCK_ON);
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    void 이력은_최신_ID_순으로_페이징_조회한다() {
        ExtensionPolicyHistory first = extensionPolicyHistoryRepository.saveAndFlush(
                ExtensionPolicyHistory.customExtensionAdded("sh")
        );
        ExtensionPolicyHistory second = extensionPolicyHistoryRepository.saveAndFlush(
                ExtensionPolicyHistory.customExtensionDeleted("sh")
        );

        Page<ExtensionPolicyHistory> page = extensionPolicyHistoryRepository
                .findAllByOrderByChangedAtDescIdDesc(PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).singleElement().satisfies(history -> {
            assertThat(history.getId()).isEqualTo(second.getId());
            assertThat(history.getId()).isGreaterThan(first.getId());
            assertThat(history.getAction()).isEqualTo(ExtensionPolicyAction.DELETE);
        });
    }
}
