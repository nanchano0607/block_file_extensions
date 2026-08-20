package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "CLAMAV_INTEGRATION_TEST", matches = "true")
class ClamAvScannerIntegrationTest {

    private static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private final ClamAvScanner scanner = new ClamAvScanner(
            System.getenv().getOrDefault("CLAMAV_HOST", "127.0.0.1"),
            Integer.parseInt(System.getenv().getOrDefault("CLAMAV_PORT", "3310")),
            10_000
    );

    @Test
    void 정상_파일은_통과한다() {
        assertThatCode(() -> scanner.scan(file("normal content"), "normal-request"))
                .doesNotThrowAnyException();
    }

    @Test
    void EICAR_테스트_문자열을_악성코드로_탐지한다() {
        assertThatThrownBy(() -> scanner.scan(file(EICAR), "eicar-request"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MALWARE_DETECTED);
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                content.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
