package com.chan.policy.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.ExtensionPolicyHistoryRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;
import com.chan.policy.repository.PolicyWriteLockRepository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.chan.policy.domain.PolicyWriteLock.CUSTOM_EXTENSION_LIMIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PolicyCommandServiceTest {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository =
            mock(FixedExtensionPolicyRepository.class);
    private final CustomExtensionRepository customExtensionRepository =
            mock(CustomExtensionRepository.class);
    private final PolicyWriteLockRepository policyWriteLockRepository =
            mock(PolicyWriteLockRepository.class);
    private final ExtensionPolicyHistoryRepository extensionPolicyHistoryRepository =
            mock(ExtensionPolicyHistoryRepository.class);

    private final PolicyCommandService policyCommandService =
            new PolicyCommandService(
                    fixedExtensionPolicyRepository,
                    customExtensionRepository,
                    policyWriteLockRepository,
                    extensionPolicyHistoryRepository
            );

    PolicyCommandServiceTest() {
        given(policyWriteLockRepository.findByNameForUpdate(CUSTOM_EXTENSION_LIMIT))
                .willReturn(Optional.of(mock(com.chan.policy.domain.PolicyWriteLock.class)));
    }

    @Test
    void 유니크_제약_위반이_아닌_DB_예외는_중복으로_뭉뚱그리지_않고_그대로_전파한다() {
        given(customExtensionRepository.existsByExtension("sh")).willReturn(false);
        given(customExtensionRepository.count()).willReturn(0L);
        DataIntegrityViolationException unrelatedFailure =
                new DataIntegrityViolationException("Data too long for column 'extension'");
        given(customExtensionRepository.saveAndFlush(any())).willThrow(unrelatedFailure);

        assertThatThrownBy(() -> policyCommandService.createCustomExtension("sh"))
                .isSameAs(unrelatedFailure);
    }

    @Test
    void 확장자_유니크_제약_위반은_중복_확장자_예외로_변환한다() {
        given(customExtensionRepository.existsByExtension("sh")).willReturn(false);
        given(customExtensionRepository.count()).willReturn(0L);
        DataIntegrityViolationException uniqueViolation = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "Duplicate entry 'sh' for key 'custom_extension.uk_custom_extension_extension'"
                )
        );
        given(customExtensionRepository.saveAndFlush(any())).willThrow(uniqueViolation);

        assertThatThrownBy(() -> policyCommandService.createCustomExtension("sh"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CUSTOM_EXTENSION_DUPLICATED);
    }
}
