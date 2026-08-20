package com.chan.policy.controller;

import com.chan.common.response.ApiResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.service.PolicyQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/policy")
public class PolicyController {

    private final PolicyQueryService policyQueryService;

    @GetMapping("/fixed-extensions")
    public ApiResponse<List<FixedExtensionResponse>> getFixedExtensions() {
        return ApiResponse.success("조회되었습니다.", policyQueryService.getFixedExtensions());
    }
}
