package com.chan.upload.domain;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ExtensionCandidateExtractor {

    private static final String EXTENSION_SEPARATOR = ".";

    private ExtensionCandidateExtractor() {
    }

    public static List<String> extract(String filename) {
        if (filename == null || !filename.contains(EXTENSION_SEPARATOR)) {
            throw new BusinessException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }

        List<String> candidates = Arrays.stream(filename.split("\\.", -1))
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty())
                .map(candidate -> candidate.toLowerCase(Locale.ROOT))
                .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }

        return candidates;
    }
}
