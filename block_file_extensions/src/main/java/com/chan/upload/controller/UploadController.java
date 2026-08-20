package com.chan.upload.controller;

import com.chan.common.response.ApiResponse;
import com.chan.upload.dto.UploadResponse;
import com.chan.upload.service.UploadService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/api/upload")
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("업로드에 성공했습니다.", uploadService.upload(file));
    }
}
