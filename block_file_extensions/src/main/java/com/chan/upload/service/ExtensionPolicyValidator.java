package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExtensionPolicyValidator {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;

    public void validate(List<String> candidates) {
        for (String candidate : candidates) {
            if (isBlocked(candidate)) {
                throw new UploadBlockedException(
                        ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED,
                        BlockReasonCategory.EXTENSION_BLOCKED,
                        "extension matched the active policy",
                        candidate
                );
            }
        }
    }

    private boolean isBlocked(String candidate) {
        return fixedExtensionPolicyRepository.existsByExtensionAndBlockedTrue(candidate)
                || customExtensionRepository.existsByExtension(candidate);
    }
}
