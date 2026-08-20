package com.chan.policy.controller;

import com.chan.policy.domain.CustomExtension;
import com.chan.policy.repository.CustomExtensionRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CustomExtensionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CustomExtensionRepository customExtensionRepository;

    @BeforeEach
    void clearCustomExtensions() {
        customExtensionRepository.deleteAllInBatch();
    }

    @Test
    void 커스텀_확장자를_정규화해_저장하고_201을_반환한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":" .Ps1 "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("등록되었습니다."))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.extension").value("ps1"))
                .andReturn();

        CustomExtension saved = customExtensionRepository.findAll().getFirst();
        Number responseId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        assertThat(customExtensionRepository.count()).isEqualTo(1);
        assertThat(saved.getId()).isEqualTo(responseId.longValue());
        assertThat(saved.getExtension()).isEqualTo("ps1");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 길이_규칙을_위반하면_400을_반환하고_저장하지_않는다() throws Exception {
        assertErrorResponse(" ", 400, "확장자는 1~20자로 입력해주세요.");
        assertThat(customExtensionRepository.count()).isZero();
    }

    @Test
    void 형식_규칙을_위반하면_400을_반환하고_저장하지_않는다() throws Exception {
        assertErrorResponse("sh!", 400, "영문/숫자만 입력 가능합니다.");
        assertThat(customExtensionRepository.count()).isZero();
    }

    @Test
    void 고정_확장자와_중복되면_409를_반환하고_저장하지_않는다() throws Exception {
        assertErrorResponse(" .EXE ", 409, "이미 고정 차단 목록에 있는 확장자입니다.");
        assertThat(customExtensionRepository.count()).isZero();
    }

    @Test
    void 기존_커스텀_확장자와_중복되면_409를_반환하고_저장하지_않는다() throws Exception {
        customExtensionRepository.saveAndFlush(new CustomExtension("sh"));

        assertErrorResponse(" SH ", 409, "이미 등록된 확장자입니다.");
        assertThat(customExtensionRepository.count()).isEqualTo(1);
    }

    @Test
    void 커스텀_확장자가_200개이면_422를_반환하고_저장하지_않는다() throws Exception {
        customExtensionRepository.saveAllAndFlush(
                IntStream.range(0, 200)
                        .mapToObj(index -> new CustomExtension("x" + index))
                        .toList()
        );

        assertErrorResponse("new", 422, "커스텀 확장자는 최대 200개까지 등록할 수 있습니다.");
        assertThat(customExtensionRepository.count()).isEqualTo(200);
    }

    private void assertErrorResponse(String extension, int status, String message) throws Exception {
        mockMvc.perform(post("/api/policy/custom-extensions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"%s"}
                                """.formatted(extension)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
