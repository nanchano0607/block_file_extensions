package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;
import com.chan.upload.domain.FileSignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class MagicNumberInspector {

    private static final Logger log = LoggerFactory.getLogger(MagicNumberInspector.class);

    public void inspect(MultipartFile file, List<String> extensionCandidates, String requestId) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(FileSignature.maximumLength());

            FileSignature.detect(header)
                    .filter(signature -> !signature.isAllowedFor(extensionCandidates))
                    .ifPresent(signature -> block(file, signature, requestId));
        } catch (IOException exception) {
            log.warn(
                    "MAGIC_NUMBER_READ_FAILED requestId={} filename={}",
                    requestId,
                    file.getOriginalFilename(),
                    exception
            );
            throw new UploadBlockedException(
                    ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED,
                    BlockReasonCategory.MAGIC_NUMBER_BLOCKED,
                    "file header could not be read"
            );
        }
    }

    private void block(MultipartFile file, FileSignature signature, String requestId) {
        log.warn(
                "MAGIC_NUMBER_BLOCKED requestId={} filename={} detectedSignature={}",
                requestId,
                file.getOriginalFilename(),
                signature
        );
        throw new UploadBlockedException(
                ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED,
                BlockReasonCategory.MAGIC_NUMBER_BLOCKED,
                "detected signature=" + signature
        );
    }
}
