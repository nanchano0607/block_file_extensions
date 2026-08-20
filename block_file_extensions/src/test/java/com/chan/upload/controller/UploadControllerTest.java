package com.chan.upload.controller;

import com.chan.upload.service.UploadService;
import com.chan.upload.service.ExtensionPolicyValidator;
import com.chan.upload.service.MimeTypeInspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@Import({UploadService.class, MimeTypeInspector.class})
@ExtendWith(OutputCaptureExtension.class)
class UploadControllerTest {

    private static final int MEBIBYTE = 1024 * 1024;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExtensionPolicyValidator extensionPolicyValidator;

    @Test
    void multipart_파일을_받아_성공_응답을_반환한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "x.txt",
                "text/plain",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("업로드에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.originalFilename").value("x.txt"))
                .andExpect(jsonPath("$.data.sizeBytes").value(5));
    }

    @Test
    void 아직_정책과_대조하지_않으므로_exe_파일도_성공한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.exe",
                "application/octet-stream",
                new byte[]{0x4d, 0x5a}
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("sample.exe"))
                .andExpect(jsonPath("$.data.sizeBytes").value(2));
    }

    @Test
    void 확장자가_없는_파일은_422로_차단한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Makefile",
                "application/octet-stream",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("허용되지 않는 파일 형식입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 점만_있는_파일은_422로_차단한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "...",
                "application/octet-stream",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("허용되지 않는 파일 형식입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 요청_MIME과_Tika_감지값이_달라도_WARN만_남기고_통과한다(CapturedOutput output) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "plain.txt",
                "application/pdf",
                "plain text content".getBytes()
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(output)
                .contains("MIME_MISMATCH")
                .contains("filename=plain.txt")
                .contains("requestedMime=application/pdf")
                .contains("detectedMime=text/plain");
    }

    @Test
    void 제한보다_작은_9MB_파일은_크기_검사를_통과한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "nine-megabytes.bin",
                "application/octet-stream",
                new byte[9 * MEBIBYTE]
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sizeBytes").value(9L * MEBIBYTE));
    }

    @Test
    void 제한과_같은_10MB_파일은_크기_검사를_통과한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ten-megabytes.bin",
                "application/octet-stream",
                new byte[10 * MEBIBYTE]
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sizeBytes").value(10L * MEBIBYTE));
    }

    @Test
    void 제한보다_큰_11MB_파일은_422로_차단한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "eleven-megabytes.bin",
                "application/octet-stream",
                new byte[11 * MEBIBYTE]
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("파일 크기 제한을 초과했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
