package com.chan.policy.domain;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedExtensionTest {

    @Test
    void 문서에_정의된_7종과_표시_순서를_관리한다() {
        assertThat(FixedExtension.values())
                .extracting(FixedExtension::value)
                .containsExactly("bat", "cmd", "com", "cpl", "exe", "scr", "js");
    }

    @Test
    void 문자열을_대소문자와_주변_공백에_관계없이_enum으로_변환한다() {
        assertThat(FixedExtension.from(" EXE ")).isEqualTo(FixedExtension.EXE);
    }

    @Test
    void 정의되지_않은_확장자는_enum으로_변환할_수_없다() {
        assertThatThrownBy(() -> FixedExtension.from("zzz"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FIXED_EXTENSION_NOT_FOUND);
    }
}
