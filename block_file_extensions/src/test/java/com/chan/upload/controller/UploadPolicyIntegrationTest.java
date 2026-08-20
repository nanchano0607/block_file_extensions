package com.chan.upload.controller;

import com.chan.policy.domain.FixedExtensionPolicy;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UploadPolicyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FixedExtensionPolicyRepository fixedExtensionPolicyRepository;

    @Autowired
    CustomExtensionRepository customExtensionRepository;

    @BeforeEach
    void resetPolicies() {
        customExtensionRepository.deleteAllInBatch();

        List<FixedExtensionPolicy> fixedPolicies = fixedExtensionPolicyRepository.findAll();
        fixedPolicies.forEach(policy -> policy.changeBlocked(false));
        fixedExtensionPolicyRepository.saveAllAndFlush(fixedPolicies);
    }

    @Test
    void 활성화한_고정_확장자가_후보에_포함되면_업로드를_차단한다() throws Exception {
        mockMvc.perform(patch("/api/policy/fixed-extensions/exe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked":true}
                                """))
                .andExpect(status().isOk());

        assertBlocked("file.exe.txt");
    }

    @Test
    void 비활성화된_고정_확장자는_업로드를_차단하지_않는다() throws Exception {
        MockMultipartFile file = file("sample.exe");

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 등록한_커스텀_확장자가_후보에_포함되면_업로드를_차단한다() throws Exception {
        mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"sh"}
                                """))
                .andExpect(status().isCreated());

        assertBlocked("script.SH.txt");
    }

    private void assertBlocked(String filename) throws Exception {
        mockMvc.perform(multipart("/api/upload").file(file(filename)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("허용되지 않는 파일 형식입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile(
                "file",
                filename,
                "application/octet-stream",
                "content".getBytes()
        );
    }
}
