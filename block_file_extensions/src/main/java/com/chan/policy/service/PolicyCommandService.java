package com.chan.policy.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.policy.domain.CustomExtension;
import com.chan.policy.domain.CustomExtensionNormalizer;
import com.chan.policy.domain.ExtensionPolicyHistory;
import com.chan.policy.domain.FixedExtension;
import com.chan.policy.domain.FixedExtensionPolicy;
import com.chan.policy.domain.PolicyConstraints;
import com.chan.policy.domain.PolicyWriteLock;
import com.chan.policy.dto.CustomExtensionItemResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.ExtensionPolicyHistoryRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;
import com.chan.policy.repository.PolicyWriteLockRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyCommandService {

    private static final String UNIQUE_EXTENSION_CONSTRAINT = "uk_custom_extension_extension";

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;
    private final PolicyWriteLockRepository policyWriteLockRepository;
    private final ExtensionPolicyHistoryRepository extensionPolicyHistoryRepository;

    public PolicyCommandService(
            FixedExtensionPolicyRepository fixedExtensionPolicyRepository,
            CustomExtensionRepository customExtensionRepository,
            PolicyWriteLockRepository policyWriteLockRepository,
            ExtensionPolicyHistoryRepository extensionPolicyHistoryRepository
    ) {
        this.fixedExtensionPolicyRepository = fixedExtensionPolicyRepository;
        this.customExtensionRepository = customExtensionRepository;
        this.policyWriteLockRepository = policyWriteLockRepository;
        this.extensionPolicyHistoryRepository = extensionPolicyHistoryRepository;
    }

    @Transactional
    public FixedExtensionResponse updateFixedExtension(String extensionValue, boolean blocked) {
        FixedExtension fixedExtension = FixedExtension.from(extensionValue);
        FixedExtensionPolicy policy = fixedExtensionPolicyRepository.findById(fixedExtension.value())
                .orElseThrow(() -> new BusinessException(ErrorCode.FIXED_EXTENSION_NOT_FOUND));

        if (policy.isBlocked() != blocked) {
            policy.changeBlocked(blocked);
            extensionPolicyHistoryRepository.save(
                    ExtensionPolicyHistory.fixedPolicyChanged(fixedExtension.value(), blocked)
            );
        }
        return FixedExtensionResponse.from(policy);
    }

    @Transactional
    public CustomExtensionItemResponse createCustomExtension(String extensionValue) {
        String normalized = CustomExtensionNormalizer.normalize(extensionValue);

        if (FixedExtension.contains(normalized)) {
            throw new BusinessException(ErrorCode.FIXED_EXTENSION_DUPLICATED);
        }

        policyWriteLockRepository.setLockWaitTimeoutToThreeSeconds();
        policyWriteLockRepository.findByNameForUpdate(PolicyWriteLock.CUSTOM_EXTENSION_LIMIT)
                .orElseThrow(() -> new IllegalStateException("커스텀 확장자 한도 락 행이 없습니다."));

        if (customExtensionRepository.existsByExtension(normalized)) {
            throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_DUPLICATED);
        }
        if (customExtensionRepository.count() >= PolicyConstraints.MAX_CUSTOM_EXTENSION_COUNT) {
            throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_LIMIT_EXCEEDED);
        }

        CustomExtension saved;
        try {
            saved = customExtensionRepository.saveAndFlush(new CustomExtension(normalized));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateExtensionViolation(exception)) {
                throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_DUPLICATED);
            }
            throw exception;
        }

        extensionPolicyHistoryRepository.save(ExtensionPolicyHistory.customExtensionAdded(normalized));
        return CustomExtensionItemResponse.from(saved);
    }

    private boolean isDuplicateExtensionViolation(DataIntegrityViolationException exception) {
        String rootCauseMessage = exception.getMostSpecificCause().getMessage();
        return rootCauseMessage != null && rootCauseMessage.contains(UNIQUE_EXTENSION_CONSTRAINT);
    }

    @Transactional
    public void deleteCustomExtension(Long id) {
        CustomExtension customExtension = customExtensionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOM_EXTENSION_NOT_FOUND));

        customExtensionRepository.delete(customExtension);
        extensionPolicyHistoryRepository.save(
                ExtensionPolicyHistory.customExtensionDeleted(customExtension.getExtension())
        );
    }
}
