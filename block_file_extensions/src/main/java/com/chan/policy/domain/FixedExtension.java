package com.chan.policy.domain;

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
            throw new IllegalArgumentException("고정 확장자는 null일 수 없습니다.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(extension -> extension.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("정의되지 않은 고정 확장자입니다: " + value));
    }
}
