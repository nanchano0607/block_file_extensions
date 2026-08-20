package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.upload.domain.ExtensionCandidateExtractor;
import com.chan.upload.dto.UploadResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class UploadService {

    private final long maxFileSizeBytes;
    private final ExtensionPolicyValidator extensionPolicyValidator;
    private final MimeTypeInspector mimeTypeInspector;
    private final MagicNumberInspector magicNumberInspector;
    private final ParserStructureInspector parserStructureInspector;

    public UploadService(
            @Value("${app.upload.max-file-size}") DataSize maxFileSize,
            ExtensionPolicyValidator extensionPolicyValidator,
            MimeTypeInspector mimeTypeInspector,
            MagicNumberInspector magicNumberInspector,
            ParserStructureInspector parserStructureInspector
    ) {
        this.maxFileSizeBytes = maxFileSize.toBytes();
        this.extensionPolicyValidator = extensionPolicyValidator;
        this.mimeTypeInspector = mimeTypeInspector;
        this.magicNumberInspector = magicNumberInspector;
        this.parserStructureInspector = parserStructureInspector;
    }

    public UploadResponse upload(MultipartFile file) {
        String requestId = UUID.randomUUID().toString();

        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.UPLOAD_FILE_SIZE_EXCEEDED);
        }

        List<String> extensionCandidates = ExtensionCandidateExtractor.extract(file.getOriginalFilename());

        extensionPolicyValidator.validate(extensionCandidates);
        mimeTypeInspector.inspect(file, requestId);
        magicNumberInspector.inspect(file, extensionCandidates, requestId);
        parserStructureInspector.inspect(file, extensionCandidates, requestId);

        return UploadResponse.pending(file.getOriginalFilename(), file.getSize());
    }
}
