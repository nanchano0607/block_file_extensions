package com.chan.upload.controller;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;
import com.chan.policy.domain.FixedExtensionPolicy;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;
import com.chan.upload.domain.UploadFile;
import com.chan.upload.domain.UploadStatus;
import com.chan.upload.repository.UploadFileRepository;
import com.chan.upload.service.ClamAvScanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UploadPolicyIntegrationTest {

    private static final int MEBIBYTE = 1024 * 1024;
    private static final Path UPLOAD_DIRECTORY = createUploadDirectory();

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.directory", () -> UPLOAD_DIRECTORY.toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FixedExtensionPolicyRepository fixedExtensionPolicyRepository;

    @Autowired
    CustomExtensionRepository customExtensionRepository;

    @Autowired
    UploadFileRepository uploadFileRepository;

    @MockitoBean
    ClamAvScanner clamAvScanner;

    @BeforeEach
    void resetPolicies() {
        uploadFileRepository.deleteAllInBatch();
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

        assertBlocked("file.exe.txt", "file,exe,txt", "exe");
    }

    @Test
    void 비활성화된_고정_확장자는_업로드를_차단하지_않는다() throws Exception {
        MockMultipartFile file = file("sample.exe");

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber());

        UploadFile saved = uploadFileRepository.findAll().getFirst();
        assertThat(saved.getStatus()).isEqualTo(UploadStatus.SUCCESS);
        assertThat(saved.getOriginalFilename()).isEqualTo("sample.exe");
        assertThat(saved.getExtensionCandidates()).isEqualTo("sample,exe");
        assertThat(saved.getMatchedExtension()).isNull();
        assertThat(saved.getBlockReasonCategory()).isNull();
        assertThat(saved.getStoredFilename()).isNotBlank();
        UUID.fromString(saved.getStoredFilename());

        Path storedPath = UPLOAD_DIRECTORY.resolve(saved.getStoredFilename());
        assertThat(Files.readAllBytes(storedPath)).isEqualTo("content".getBytes());
        assertThat(Files.getPosixFilePermissions(storedPath)).isEqualTo(Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
        ));
    }

    @Test
    void 등록한_커스텀_확장자가_후보에_포함되면_업로드를_차단한다() throws Exception {
        mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"sh"}
                                """))
                .andExpect(status().isCreated());

        assertBlocked("script.SH.txt", "script,sh,txt", "sh");
    }

    @Test
    void 크기_초과_사유를_DB에_기록한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                new byte[11 * MEBIBYTE]
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isUnprocessableContent());

        assertBlockedRecord(BlockReasonCategory.SIZE_EXCEEDED, null, null);
    }

    @Test
    void 확장자_없는_파일의_차단_사유를_DB에_기록한다() throws Exception {
        mockMvc.perform(multipart("/api/upload").file(file("Makefile")))
                .andExpect(status().isUnprocessableContent());

        assertBlockedRecord(BlockReasonCategory.EXTENSION_BLOCKED, null, null);
    }

    @Test
    void 매직넘버_차단_사유를_DB에_기록한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "renamed.txt",
                "text/plain",
                new byte[]{0x4D, 0x5A, 0x00, 0x00}
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isUnprocessableContent());

        assertBlockedRecord(BlockReasonCategory.MAGIC_NUMBER_BLOCKED, "renamed,txt", null);
    }

    @Test
    void Parser_구조_실패_사유를_DB에_기록한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isUnprocessableContent());

        assertBlockedRecord(BlockReasonCategory.PARSER_STRUCTURE_INVALID, "broken,png", null);
    }

    @Test
    void 악성코드_탐지_사유를_DB에_기록한다() throws Exception {
        willThrow(new UploadBlockedException(
                ErrorCode.MALWARE_DETECTED,
                BlockReasonCategory.MALWARE_DETECTED,
                "Eicar-Test-Signature FOUND"
        )).given(clamAvScanner).scan(any(), anyString());

        mockMvc.perform(multipart("/api/upload").file(file("eicar.txt")))
                .andExpect(status().isUnprocessableContent());

        assertBlockedRecord(BlockReasonCategory.MALWARE_DETECTED, "eicar,txt", null);
    }

    @Test
    void ClamAV_장애_사유를_DB에_기록한다() throws Exception {
        willThrow(new UploadBlockedException(
                ErrorCode.MALWARE_SCAN_FAILED,
                BlockReasonCategory.MALWARE_SCAN_FAILED,
                "connection timed out"
        )).given(clamAvScanner).scan(any(), anyString());

        mockMvc.perform(multipart("/api/upload").file(file("normal.txt")))
                .andExpect(status().isInternalServerError());

        assertBlockedRecord(BlockReasonCategory.MALWARE_SCAN_FAILED, "normal,txt", null);
    }

    private void assertBlocked(
            String filename,
            String extensionCandidates,
            String matchedExtension
    ) throws Exception {
        mockMvc.perform(multipart("/api/upload").file(file(filename)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("허용되지 않는 파일 형식입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        UploadFile blocked = uploadFileRepository.findAll().getFirst();
        assertThat(blocked.getStatus()).isEqualTo(UploadStatus.BLOCKED);
        assertThat(blocked.getStoredFilename()).isNull();
        assertThat(blocked.getExtensionCandidates()).isEqualTo(extensionCandidates);
        assertThat(blocked.getMatchedExtension()).isEqualTo(matchedExtension);
        assertThat(blocked.getBlockReasonCategory())
                .isEqualTo(BlockReasonCategory.EXTENSION_BLOCKED);
    }

    private void assertBlockedRecord(
            BlockReasonCategory category,
            String extensionCandidates,
            String matchedExtension
    ) {
        assertThat(uploadFileRepository.findAll()).singleElement().satisfies(blocked -> {
            assertThat(blocked.getStatus()).isEqualTo(UploadStatus.BLOCKED);
            assertThat(blocked.getStoredFilename()).isNull();
            assertThat(blocked.getExtensionCandidates()).isEqualTo(extensionCandidates);
            assertThat(blocked.getMatchedExtension()).isEqualTo(matchedExtension);
            assertThat(blocked.getBlockReasonCategory()).isEqualTo(category);
        });
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile(
                "file",
                filename,
                "application/octet-stream",
                "content".getBytes()
        );
    }

    private static Path createUploadDirectory() {
        try {
            return Files.createTempDirectory("file-guard-upload-test-");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create upload test directory", exception);
        }
    }
}
