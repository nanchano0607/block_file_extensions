package com.chan.policy.infrastructure.persistence;

import com.chan.policy.domain.CustomExtension;
import com.chan.policy.repository.CustomExtensionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomExtensionRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    CustomExtensionRepository customExtensionRepository;

    @Test
    void 커스텀_확장자를_저장하면_생성_시각이_함께_기록된다() {
        // Arrange
        CustomExtension extension = new CustomExtension("sh");

        // Act
        CustomExtension saved = customExtensionRepository.saveAndFlush(extension);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getExtension()).isEqualTo("sh");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 동일한_확장자를_중복_저장하면_DB_제약으로_실패한다() {
        // Arrange
        customExtensionRepository.saveAndFlush(new CustomExtension("sh"));

        // Act & Assert
        assertThatThrownBy(() -> customExtensionRepository.saveAndFlush(new CustomExtension("sh")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
