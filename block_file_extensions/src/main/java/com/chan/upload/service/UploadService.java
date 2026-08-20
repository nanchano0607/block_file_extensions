package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.upload.domain.ExtensionCandidateExtractor;
import com.chan.upload.dto.UploadResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private final long maxFileSizeBytes;
    private final ExtensionPolicyValidator extensionPolicyValidator;

    public UploadService(
            @Value("${app.upload.max-file-size}") DataSize maxFileSize,
            ExtensionPolicyValidator extensionPolicyValidator
    ) {
        this.maxFileSizeBytes = maxFileSize.toBytes();
        this.extensionPolicyValidator = extensionPolicyValidator;
    }

    public UploadResponse upload(MultipartFile file) {
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.UPLOAD_FILE_SIZE_EXCEEDED);
        }

        extensionPolicyValidator.validate(
                ExtensionCandidateExtractor.extract(file.getOriginalFilename())
        );

        return UploadResponse.pending(file.getOriginalFilename(), file.getSize());
    }
}
