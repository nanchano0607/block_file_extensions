package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;
import com.chan.upload.domain.ExtensionCandidateExtractor;
import com.chan.upload.dto.UploadResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final long maxFileSizeBytes;
    private final ExtensionPolicyValidator extensionPolicyValidator;
    private final MimeTypeInspector mimeTypeInspector;
    private final MagicNumberInspector magicNumberInspector;
    private final ParserStructureInspector parserStructureInspector;
    private final ClamAvScanner clamAvScanner;
    private final FileStorageService fileStorageService;
    private final UploadFileRecordService uploadFileRecordService;

    public UploadService(
            @Value("${app.upload.max-file-size}") DataSize maxFileSize,
            ExtensionPolicyValidator extensionPolicyValidator,
            MimeTypeInspector mimeTypeInspector,
            MagicNumberInspector magicNumberInspector,
            ParserStructureInspector parserStructureInspector,
            ClamAvScanner clamAvScanner,
            FileStorageService fileStorageService,
            UploadFileRecordService uploadFileRecordService
    ) {
        this.maxFileSizeBytes = maxFileSize.toBytes();
        this.extensionPolicyValidator = extensionPolicyValidator;
        this.mimeTypeInspector = mimeTypeInspector;
        this.magicNumberInspector = magicNumberInspector;
        this.parserStructureInspector = parserStructureInspector;
        this.clamAvScanner = clamAvScanner;
        this.fileStorageService = fileStorageService;
        this.uploadFileRecordService = uploadFileRecordService;
    }

    public UploadResponse upload(MultipartFile file) {
        String requestId = UUID.randomUUID().toString();
        String originalFilename = originalFilename(file);
        List<String> extensionCandidates = List.of();

        try {
            if (file.getSize() > maxFileSizeBytes) {
                throw new UploadBlockedException(
                        ErrorCode.UPLOAD_FILE_SIZE_EXCEEDED,
                        BlockReasonCategory.SIZE_EXCEEDED,
                        "sizeBytes=" + file.getSize() + ", maxBytes=" + maxFileSizeBytes
                );
            }

            extensionCandidates = ExtensionCandidateExtractor.extract(originalFilename);

            extensionPolicyValidator.validate(extensionCandidates);
            mimeTypeInspector.inspect(file, requestId);
            magicNumberInspector.inspect(file, extensionCandidates, requestId);
            parserStructureInspector.inspect(file, extensionCandidates, requestId);
            clamAvScanner.scan(file, requestId);

            StoredFile storedFile = fileStorageService.store(file);
            Long uploadFileId = recordSuccessOrDeleteFile(
                    originalFilename,
                    file.getSize(),
                    extensionCandidates,
                    storedFile
            );
            return UploadResponse.success(uploadFileId, originalFilename, file.getSize());
        } catch (BusinessException exception) {
            BlockedContext context = blockedContext(exception);
            recordBlocked(
                    requestId,
                    originalFilename,
                    file.getSize(),
                    extensionCandidates,
                    context,
                    exception
            );
            throw exception;
        }
    }

    private Long recordSuccessOrDeleteFile(
            String originalFilename,
            long sizeBytes,
            List<String> extensionCandidates,
            StoredFile storedFile
    ) {
        try {
            return uploadFileRecordService.recordSuccess(
                    originalFilename,
                    storedFile.filename(),
                    sizeBytes,
                    extensionCandidates
            );
        } catch (RuntimeException exception) {
            fileStorageService.deleteQuietly(storedFile.path());
            throw exception;
        }
    }

    private void recordBlocked(
            String requestId,
            String originalFilename,
            long sizeBytes,
            List<String> extensionCandidates,
            BlockedContext context,
            BusinessException originalException
    ) {
        String detail = originalException instanceof UploadBlockedException blockedException
                ? blockedException.getDetail()
                : originalException.getErrorCode().name();
        log.warn(
                "UPLOAD_BLOCKED requestId={} filename={} category={} detail={}",
                requestId,
                originalFilename,
                context.category(),
                detail
        );

        try {
            uploadFileRecordService.recordBlocked(
                    originalFilename,
                    sizeBytes,
                    extensionCandidates,
                    context.matchedExtension(),
                    context.category()
            );
        } catch (RuntimeException recordException) {
            log.error(
                    "UPLOAD_BLOCKED_RECORD_FAILED requestId={} filename={} category={}",
                    requestId,
                    originalFilename,
                    context.category(),
                    recordException
            );
            originalException.addSuppressed(recordException);
        }
    }

    private BlockedContext blockedContext(BusinessException exception) {
        if (exception instanceof UploadBlockedException blockedException) {
            return new BlockedContext(
                    blockedException.getCategory(),
                    blockedException.getMatchedExtension()
            );
        }
        return new BlockedContext(categoryOf(exception.getErrorCode()), null);
    }

    private BlockReasonCategory categoryOf(ErrorCode errorCode) {
        return switch (errorCode) {
            case UPLOAD_FILE_SIZE_EXCEEDED -> BlockReasonCategory.SIZE_EXCEEDED;
            case UPLOAD_FILE_PROCESSING_FAILED -> BlockReasonCategory.PARSER_STRUCTURE_INVALID;
            case MALWARE_DETECTED -> BlockReasonCategory.MALWARE_DETECTED;
            case MALWARE_SCAN_FAILED -> BlockReasonCategory.MALWARE_SCAN_FAILED;
            case UPLOAD_FILE_STORAGE_FAILED -> BlockReasonCategory.STORAGE_FAILED;
            default -> BlockReasonCategory.EXTENSION_BLOCKED;
        };
    }

    private String originalFilename(MultipartFile file) {
        return file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    }

    private record BlockedContext(
            BlockReasonCategory category,
            String matchedExtension
    ) {
    }
}
