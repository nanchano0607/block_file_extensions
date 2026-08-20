package com.chan.common.exception;

import com.chan.policy.controller.PolicyController;
import com.chan.policy.service.PolicyCommandService;
import com.chan.policy.service.PolicyQueryService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PolicyQueryService policyQueryService;

    @MockitoBean
    PolicyCommandService policyCommandService;

    @Test
    void 요청_바디_검증에_실패하면_공통_응답_규격으로_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/policy/fixed-extensions/exe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 경로변수_타입이_맞지_않으면_공통_응답_규격으로_400을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/policy/custom-extensions/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 예상치_못한_예외는_공통_응답_규격으로_500을_반환한다() throws Exception {
        given(policyQueryService.getFixedExtensions()).willThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/policy/fixed-extensions"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
