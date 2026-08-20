package com.chan.upload.service;

import com.chan.upload.dto.UploadResponse;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    public UploadResponse upload(MultipartFile file) {
        return UploadResponse.pending(file.getOriginalFilename(), file.getSize());
    }
}
