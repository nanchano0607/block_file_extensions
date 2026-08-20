package com.chan.policy.controller;

import com.chan.common.response.ApiResponse;
import com.chan.policy.dto.CreateCustomExtensionRequest;
import com.chan.policy.dto.CustomExtensionItemResponse;
import com.chan.policy.dto.CustomExtensionListResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.dto.UpdateFixedExtensionRequest;
import com.chan.policy.service.PolicyCommandService;
import com.chan.policy.service.PolicyQueryService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/custom-extensions")
    public ResponseEntity<ApiResponse<CustomExtensionItemResponse>> createCustomExtension(
            @RequestBody CreateCustomExtensionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "등록되었습니다.",
                        policyCommandService.createCustomExtension(request.extension())
                ));
    }

    @DeleteMapping("/custom-extensions/{id}")
    public ApiResponse<Void> deleteCustomExtension(@PathVariable Long id) {
        policyCommandService.deleteCustomExtension(id);
        return ApiResponse.success("삭제되었습니다.", null);
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
