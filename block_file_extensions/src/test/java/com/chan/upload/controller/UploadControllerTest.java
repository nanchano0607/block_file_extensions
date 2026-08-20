package com.chan.upload.controller;

import com.chan.upload.service.UploadService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@Import(UploadService.class)
class UploadControllerTest {

    @Autowired
    MockMvc mockMvc;

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
    void 아직_확장자를_검증하지_않으므로_exe_파일도_성공한다() throws Exception {
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
}
