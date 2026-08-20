package com.chan.policy.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PolicyCommandServiceTest {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository =
            mock(FixedExtensionPolicyRepository.class);
    private final CustomExtensionRepository customExtensionRepository =
            mock(CustomExtensionRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final PolicyCommandService policyCommandService =
            new PolicyCommandService(fixedExtensionPolicyRepository, customExtensionRepository, transactionManager);

    @BeforeEach
    void stubTransactionManager() {
        given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
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
