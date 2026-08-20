package com.chan.policy.domain;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import java.util.Arrays;
import java.util.Locale;

public enum FixedExtension {
    BAT("bat"),
    CMD("cmd"),
    COM("com"),
    CPL("cpl"),
    EXE("exe"),
    SCR("scr"),
    JS("js");

    private final String value;

    FixedExtension(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static FixedExtension from(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.FIXED_EXTENSION_NOT_FOUND);
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(extension -> extension.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FIXED_EXTENSION_NOT_FOUND));
    }
}
