package com.chan.policy.service;

import com.chan.common.exception.BusinessException;
import com.chan.policy.domain.FixedExtension;
import com.chan.policy.domain.FixedExtensionPolicy;
import com.chan.policy.dto.FixedExtensionResponse;
import com.chan.policy.repository.FixedExtensionPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PolicyCommandService {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;

    public FixedExtensionResponse updateFixedExtension(String extensionValue, boolean blocked) {
        FixedExtension fixedExtension = parseFixedExtension(extensionValue);
        FixedExtensionPolicy policy = fixedExtensionPolicyRepository.findById(fixedExtension.value())
                .orElseThrow(PolicyCommandService::fixedExtensionNotFound);

        policy.changeBlocked(blocked);
        return FixedExtensionResponse.from(policy);
    }

    private FixedExtension parseFixedExtension(String value) {
        try {
            return FixedExtension.from(value);
        } catch (IllegalArgumentException exception) {
            throw fixedExtensionNotFound();
        }
    }

    private static BusinessException fixedExtensionNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "정의되지 않은 고정 확장자입니다.");
    }
}
