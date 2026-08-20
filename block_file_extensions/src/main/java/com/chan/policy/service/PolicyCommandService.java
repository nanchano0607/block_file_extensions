package com.chan.policy.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.policy.domain.CustomExtension;
import com.chan.policy.domain.CustomExtensionNormalizer;
import com.chan.policy.domain.FixedExtension;
import com.chan.policy.domain.FixedExtensionPolicy;
import com.chan.policy.domain.PolicyConstraints;
import com.chan.policy.dto.CustomExtensionItemResponse;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PolicyCommandService {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;

    public FixedExtensionResponse updateFixedExtension(String extensionValue, boolean blocked) {
        FixedExtension fixedExtension = FixedExtension.from(extensionValue);
        FixedExtensionPolicy policy = fixedExtensionPolicyRepository.findById(fixedExtension.value())
                .orElseThrow(() -> new BusinessException(ErrorCode.FIXED_EXTENSION_NOT_FOUND));

        policy.changeBlocked(blocked);
        return FixedExtensionResponse.from(policy);
    }

    public CustomExtensionItemResponse createCustomExtension(String extensionValue) {
        String normalized = CustomExtensionNormalizer.normalize(extensionValue);

        if (FixedExtension.contains(normalized)) {
            throw new BusinessException(ErrorCode.FIXED_EXTENSION_DUPLICATED);
        }
        if (customExtensionRepository.existsByExtension(normalized)) {
            throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_DUPLICATED);
        }
        if (customExtensionRepository.count() >= PolicyConstraints.MAX_CUSTOM_EXTENSION_COUNT) {
            throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_LIMIT_EXCEEDED);
        }

        try {
            CustomExtension saved = customExtensionRepository.saveAndFlush(new CustomExtension(normalized));
            return CustomExtensionItemResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_DUPLICATED);
        }
    }

    public void deleteCustomExtension(Long id) {
        CustomExtension customExtension = customExtensionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOM_EXTENSION_NOT_FOUND));

        customExtensionRepository.delete(customExtension);
    }
}
