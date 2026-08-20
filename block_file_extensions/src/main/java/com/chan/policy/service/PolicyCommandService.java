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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PolicyCommandService {

    private static final String UNIQUE_EXTENSION_CONSTRAINT = "uk_custom_extension_extension";

    // 200개 한도 체크(count)와 저장(insert)이 원자적이지 않아 동시 요청이 한도를 넘길 수 있다.
    // 단일 EC2 인스턴스·단일 관리자 전제(general.md 10) 하에서는 JVM 내 직렬화만으로 충분히 막을 수 있어
    // DB 제약 대신 이 락으로 처리한다 (spec.md 2-3: 200은 애플리케이션 레벨에서만 검사).
    // @Transactional(선언적 AOP)은 메서드가 끝난 뒤에 커밋되므로 synchronized 블록 안에서 커밋까지
    // 마쳐야 락이 실제로 레이스를 막는다 — 그래서 이 메서드만 TransactionTemplate으로 직접 커밋 시점을 제어한다.
    private final Object customExtensionWriteLock = new Object();

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository;
    private final CustomExtensionRepository customExtensionRepository;
    private final TransactionTemplate transactionTemplate;

    public PolicyCommandService(
            FixedExtensionPolicyRepository fixedExtensionPolicyRepository,
            CustomExtensionRepository customExtensionRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.fixedExtensionPolicyRepository = fixedExtensionPolicyRepository;
        this.customExtensionRepository = customExtensionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
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

        synchronized (customExtensionWriteLock) {
            return transactionTemplate.execute(status -> {
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
                    if (isDuplicateExtensionViolation(exception)) {
                        throw new BusinessException(ErrorCode.CUSTOM_EXTENSION_DUPLICATED);
                    }
                    throw exception;
                }
            });
        }
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
    }
}
