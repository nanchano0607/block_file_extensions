package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExtensionPolicyValidator {

    private static final Logger log = LoggerFactory.getLogger(ExtensionPolicyValidator.class);

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;

    public void validate(List<String> candidates, String requestId) {
        if (candidates.isEmpty()) {
            log.info(
                    "UPLOAD_STAGE_RESULT requestId={} stage=1 stageName=EXTENSION_POLICY status=SKIPPED reason=NO_CANDIDATES",
                    requestId
            );
            return;
        }

        // 후보마다 두 리포지토리를 각각 조회하면 이중/다중 확장자에서 왕복이 후보 수에 비례해 늘어난다.
        // 정책 대조 전체를 각 저장소당 IN 조회 한 번으로 배치 처리하고, 실제 판정(첫 매칭 후보 선택)은
        // 원래의 파일명 순서를 그대로 유지한 메모리 내 순회로 수행해 기존 동작을 그대로 보존한다.
        Set<String> blocked = new HashSet<>(fixedExtensionPolicyRepository.findBlockedExtensionsAmong(candidates));
        blocked.addAll(customExtensionRepository.findRegisteredExtensionsAmong(candidates));

        for (String candidate : candidates) {
            if (blocked.contains(candidate)) {
                log.warn(
                        "UPLOAD_STAGE_RESULT requestId={} stage=1 stageName=EXTENSION_POLICY status=BLOCKED matchedExtension={} candidates={}",
                        requestId,
                        candidate,
                        candidates
                );
                throw new UploadBlockedException(
                        ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED,
                        BlockReasonCategory.EXTENSION_BLOCKED,
                        "extension matched the active policy",
                        candidate
                );
            }
        }

        log.info(
                "UPLOAD_STAGE_RESULT requestId={} stage=1 stageName=EXTENSION_POLICY status=PASSED candidates={}",
                requestId,
                candidates
        );
    }
}
