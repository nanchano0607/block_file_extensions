package com.chan.policy.domain;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomExtensionNormalizerTest {

    @ParameterizedTest
    @MethodSource("validExtensions")
    void 커스텀_확장자를_정규화한다(String input, String expected) {
        assertThat(CustomExtensionNormalizer.normalize(input)).isEqualTo(expected);
    }

    private static Stream<Arguments> validExtensions() {
        return Stream.of(
                Arguments.of("sh", "sh"),
                Arguments.of(" .SH ", "sh"),
                Arguments.of(".Ps1", "ps1"),
                Arguments.of("A1B2", "a1b2"),
                Arguments.of("123", "123"),
                Arguments.of("abcdefghijklmnopqrst", "abcdefghijklmnopqrst")
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", ".", " . ", "abcdefghijklmnopqrstu"})
    void 정규화한_확장자의_길이가_1에서_20자_사이가_아니면_거부한다(String input) {
        assertThatThrownBy(() -> CustomExtensionNormalizer.normalize(input))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CUSTOM_EXTENSION_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "sh!", "한글", "é", "..sh", "a_b", "a.sh"})
    void 영문과_숫자_외의_문자가_포함되면_거부한다(String input) {
        assertThatThrownBy(() -> CustomExtensionNormalizer.normalize(input))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CUSTOM_EXTENSION_FORMAT);
    }

    @Test
    void 선행점_제거_후의_길이를_검사한다() {
        String twentyCharactersWithLeadingDot = ".abcdefghijklmnopqrst";

        assertThat(CustomExtensionNormalizer.normalize(twentyCharactersWithLeadingDot))
                .isEqualTo("abcdefghijklmnopqrst");
    }
}
