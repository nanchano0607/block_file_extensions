package com.chan.policy.controller;

import com.chan.common.exception.BusinessException;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.service.PolicyCommandService;
import com.chan.policy.service.PolicyQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PolicyQueryService policyQueryService;

    @MockitoBean
    PolicyCommandService policyCommandService;

    @Test
    void 고정_확장자_7종을_공통_응답_형식으로_조회한다() throws Exception {
        given(policyQueryService.getFixedExtensions()).willReturn(List.of(
                new FixedExtensionResponse("bat", false),
                new FixedExtensionResponse("cmd", false),
                new FixedExtensionResponse("com", false),
                new FixedExtensionResponse("cpl", false),
                new FixedExtensionResponse("exe", true),
                new FixedExtensionResponse("scr", false),
                new FixedExtensionResponse("js", false)
        ));

        mockMvc.perform(get("/api/policy/fixed-extensions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("조회되었습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].extension").value("bat"))
                .andExpect(jsonPath("$.data[0].blocked").value(false))
                .andExpect(jsonPath("$.data[4].extension").value("exe"))
                .andExpect(jsonPath("$.data[4].blocked").value(true));
    }

    @Test
    void 정의된_고정_확장자의_차단_상태를_변경한다() throws Exception {
        given(policyCommandService.updateFixedExtension("exe", true))
                .willReturn(new FixedExtensionResponse("exe", true));

        mockMvc.perform(patch("/api/policy/fixed-extensions/exe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("저장되었습니다."))
                .andExpect(jsonPath("$.data.extension").value("exe"))
                .andExpect(jsonPath("$.data.blocked").value(true));
    }

    @Test
    void 정의되지_않은_고정_확장자는_404를_반환한다() throws Exception {
        given(policyCommandService.updateFixedExtension("zip", true))
                .willThrow(new BusinessException(HttpStatus.NOT_FOUND, "정의되지 않은 고정 확장자입니다."));

        mockMvc.perform(patch("/api/policy/fixed-extensions/zip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked": true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("정의되지 않은 고정 확장자입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
