package com.chan.policy.domain;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CustomExtensionNormalizer {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-z0-9]+$");

    private CustomExtensionNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_CUSTOM_EXTENSION_LENGTH);
        }

        String normalized = input.trim();
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);

        if (normalized.isEmpty() || normalized.length() > PolicyConstraints.MAX_EXTENSION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_CUSTOM_EXTENSION_LENGTH);
        }
        if (!ALPHANUMERIC_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_CUSTOM_EXTENSION_FORMAT);
        }

        return normalized;
    }
}
