package com.chan.policy.controller;

import com.chan.common.response.ApiResponse;
import com.chan.policy.dto.CustomExtensionListResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.dto.UpdateFixedExtensionRequest;
import com.chan.policy.service.PolicyCommandService;
import com.chan.policy.service.PolicyQueryService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/policy")
public class PolicyController {

    private final PolicyQueryService policyQueryService;
    private final PolicyCommandService policyCommandService;

    @GetMapping("/fixed-extensions")
    public ApiResponse<List<FixedExtensionResponse>> getFixedExtensions() {
        return ApiResponse.success("조회되었습니다.", policyQueryService.getFixedExtensions());
    }

    @GetMapping("/custom-extensions")
    public ApiResponse<CustomExtensionListResponse> getCustomExtensions() {
        return ApiResponse.success("조회되었습니다.", policyQueryService.getCustomExtensions());
    }

    @PatchMapping("/fixed-extensions/{extension}")
    public ApiResponse<FixedExtensionResponse> updateFixedExtension(
            @PathVariable String extension,
            @Valid @RequestBody UpdateFixedExtensionRequest request
    ) {
        return ApiResponse.success(
                "저장되었습니다.",
                policyCommandService.updateFixedExtension(extension, request.blocked())
        );
    }
}
