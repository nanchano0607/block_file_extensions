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
import java.util.Optional;

@Service
public class MagicNumberInspector {

    private static final Logger log = LoggerFactory.getLogger(MagicNumberInspector.class);

    public void inspect(MultipartFile file, List<String> extensionCandidates, String requestId) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(FileSignature.maximumLength());
            Optional<FileSignature> detectedSignature = FileSignature.detect(header);

            if (detectedSignature.isEmpty()) {
                log.info(
                        "UPLOAD_STAGE_RESULT requestId={} stage=3 stageName=MAGIC_NUMBER status=SKIPPED reason=SIGNATURE_NOT_RECOGNIZED filename={} candidates={}",
                        requestId,
                        file.getOriginalFilename(),
                        extensionCandidates
                );
                return;
            }

            FileSignature signature = detectedSignature.get();
            if (!signature.isAllowedFor(extensionCandidates)) {
                block(file, signature, requestId);
            }

            log.info(
                    "UPLOAD_STAGE_RESULT requestId={} stage=3 stageName=MAGIC_NUMBER status=PASSED filename={} detectedSignature={} candidates={}",
                    requestId,
                    file.getOriginalFilename(),
                    signature,
                    extensionCandidates
            );
        } catch (IOException exception) {
            log.warn(
                    "UPLOAD_STAGE_RESULT requestId={} stage=3 stageName=MAGIC_NUMBER status=BLOCKED reason=HEADER_READ_FAILED filename={}",
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
                "UPLOAD_STAGE_RESULT requestId={} stage=3 stageName=MAGIC_NUMBER status=BLOCKED reason=SIGNATURE_MISMATCH filename={} detectedSignature={}",
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
