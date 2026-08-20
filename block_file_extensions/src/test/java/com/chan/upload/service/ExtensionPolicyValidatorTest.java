package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;
import com.chan.policy.repository.CustomExtensionRepository;
import com.chan.policy.repository.FixedExtensionPolicyRepository;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ExtensionPolicyValidatorTest {

    private final FixedExtensionPolicyRepository fixedExtensionPolicyRepository =
            mock(FixedExtensionPolicyRepository.class);
    private final CustomExtensionRepository customExtensionRepository =
            mock(CustomExtensionRepository.class);

    private final ExtensionPolicyValidator validator =
            new ExtensionPolicyValidator(fixedExtensionPolicyRepository, customExtensionRepository);

    @Test
    void 후보_중_어느_것도_차단되지_않으면_통과한다() {
        given(fixedExtensionPolicyRepository.findBlockedExtensionsAmong(any())).willReturn(List.of());
        given(customExtensionRepository.findRegisteredExtensionsAmong(any())).willReturn(List.of());

        assertThatCode(() -> validator.validate(List.of("report", "pdf"), "request-id"))
                .doesNotThrowAnyException();
    }

    @Test
    void 고정_확장자가_차단되어_있으면_해당_후보로_차단한다() {
        given(fixedExtensionPolicyRepository.findBlockedExtensionsAmong(any())).willReturn(List.of("exe"));
        given(customExtensionRepository.findRegisteredExtensionsAmong(any())).willReturn(List.of());

        assertThatThrownBy(() -> validator.validate(List.of("file", "exe", "txt"), "request-id"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 커스텀_확장자가_등록되어_있으면_해당_후보로_차단한다() {
        given(fixedExtensionPolicyRepository.findBlockedExtensionsAmong(any())).willReturn(List.of());
        given(customExtensionRepository.findRegisteredExtensionsAmong(any())).willReturn(List.of("sh"));

        assertThatThrownBy(() -> validator.validate(List.of("script", "sh", "txt"), "request-id"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 후보_개수와_무관하게_리포지토리는_각각_한_번만_조회한다() {
        given(fixedExtensionPolicyRepository.findBlockedExtensionsAmong(any())).willReturn(List.of());
        given(customExtensionRepository.findRegisteredExtensionsAmong(any())).willReturn(List.of());

        validator.validate(List.of("a", "b", "c", "d", "e"), "request-id");

        verify(fixedExtensionPolicyRepository, times(1)).findBlockedExtensionsAmong(any());
        verify(customExtensionRepository, times(1)).findRegisteredExtensionsAmong(any());
    }
}
