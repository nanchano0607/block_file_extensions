package com.chan.upload.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MimeTypeInspectorTest {

    private static final String REQUEST_ID = "request-id";

    private final MimeTypeInspector inspector = new MimeTypeInspector();

    @Test
    void 요청된_MIME과_실제_MIME이_같으면_통과한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.txt", "text/plain", "plain text content".getBytes()
        );

        assertThatCode(() -> inspector.inspect(file, REQUEST_ID)).doesNotThrowAnyException();
    }

    @Test
    void 요청된_MIME과_실제_MIME이_달라도_차단하지_않고_로그만_남긴다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "plain text content".getBytes()
        );

        assertThatCode(() -> inspector.inspect(file, REQUEST_ID)).doesNotThrowAnyException();
    }

    @Test
    void MIME_감지_중_런타임_예외가_발생해도_차단하지_않고_로그만_남긴다() throws Exception {
        // Tika의 내부 포맷 감지기는 손상된 입력에 대해 IOException이 아닌 RuntimeException을
        // 던지기도 한다. 이 경우에도 "MIME은 절대 차단 근거로 쓰지 않는다"는 원칙이 지켜져야 한다.
        MultipartFile file = mock(MultipartFile.class);
        given(file.getContentType()).willReturn("application/pdf");
        given(file.getOriginalFilename()).willReturn("broken.pdf");
        given(file.getInputStream()).willAnswer(invocation -> new InputStream() {
            @Override
            public int read() {
                throw new RuntimeException("Tika internal failure");
            }
        });

        assertThatCode(() -> inspector.inspect(file, REQUEST_ID)).doesNotThrowAnyException();
    }
}
