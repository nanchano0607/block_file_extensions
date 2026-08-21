package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagicNumberInspectorTest {

    private static final String REQUEST_ID = "request-id";

    private final MagicNumberInspector inspector = new MagicNumberInspector();

    @Test
    void 알려진_시그니처가_없으면_통과한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.txt", "text/plain", "plain text content".getBytes()
        );

        assertThatCode(() -> inspector.inspect(file, List.of("txt"), REQUEST_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void 시그니처가_후보_확장자와_호환되면_통과한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A}
        );

        assertThatCode(() -> inspector.inspect(file, List.of("png"), REQUEST_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void 실행파일_시그니처는_후보_확장자와_무관하게_차단한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "renamed.txt", "text/plain",
                new byte[]{0x4D, 0x5A, 0x00, 0x00}
        );

        assertThatThrownBy(() -> inspector.inspect(file, List.of("txt"), REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 시그니처가_후보_확장자와_호환되지_않으면_차단한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "disguised.txt", "text/plain",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A}
        );

        assertThatThrownBy(() -> inspector.inspect(file, List.of("txt"), REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
    }
}
