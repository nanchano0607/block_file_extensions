package com.chan.upload.domain;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionCandidateExtractorTest {

    @ParameterizedTest
    @MethodSource("filenames")
    void 파일명에서_확장자_후보를_추출한다(String filename, List<String> expected) {
        assertThat(ExtensionCandidateExtractor.extract(filename)).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> filenames() {
        return Stream.of(
                Arguments.of("file.exe.txt", List.of("file", "exe", "txt")),
                Arguments.of(".env", List.of("env")),
                Arguments.of("abc.", List.of("abc")),
                Arguments.of("....abc", List.of("abc")),
                Arguments.of("file.PDF", List.of("file", "pdf")),
                Arguments.of(" report . EXE . txt ", List.of("report", "exe", "txt")),
                Arguments.of("archive..EXE...txt", List.of("archive", "exe", "txt"))
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Makefile", "README", ".", "...", " . "})
    void 확장자_후보가_없으면_차단한다(String filename) {
        assertThatThrownBy(() -> ExtensionCandidateExtractor.extract(filename))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
    }
}
