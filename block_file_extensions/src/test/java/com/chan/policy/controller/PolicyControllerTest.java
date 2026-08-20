package com.chan.policy.controller;

import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.service.PolicyQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PolicyQueryService policyQueryService;

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
}
