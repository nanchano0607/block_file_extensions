package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.upload.domain.UploadFile;
import com.chan.upload.repository.UploadFileRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UploadFileRecordService {

    private final UploadFileRepository uploadFileRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordSuccess(
            String originalFilename,
            String storedFilename,
            long sizeBytes,
            List<String> extensionCandidates
    ) {
        UploadFile uploadFile = UploadFile.success(
                originalFilename,
                storedFilename,
                join(extensionCandidates),
                sizeBytes
        );
        return uploadFileRepository.save(uploadFile).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlocked(
            String originalFilename,
            long sizeBytes,
            List<String> extensionCandidates,
            String matchedExtension,
            BlockReasonCategory category
    ) {
        uploadFileRepository.save(UploadFile.blocked(
                originalFilename,
                join(extensionCandidates),
                matchedExtension,
                sizeBytes,
                category
        ));
    }

    private String join(List<String> extensionCandidates) {
        return extensionCandidates.isEmpty() ? null : String.join(",", extensionCandidates);
    }
}
