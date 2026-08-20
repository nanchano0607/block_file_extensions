package com.chan.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    FIXED_EXTENSION_NOT_FOUND(HttpStatus.NOT_FOUND, "정의되지 않은 고정 확장자입니다."),
    INVALID_CUSTOM_EXTENSION_LENGTH(HttpStatus.BAD_REQUEST, "확장자는 1~20자로 입력해주세요."),
    INVALID_CUSTOM_EXTENSION_FORMAT(HttpStatus.BAD_REQUEST, "영문/숫자만 입력 가능합니다."),
    FIXED_EXTENSION_DUPLICATED(HttpStatus.CONFLICT, "이미 고정 차단 목록에 있는 확장자입니다."),
    CUSTOM_EXTENSION_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 확장자입니다."),
    CUSTOM_EXTENSION_LIMIT_EXCEEDED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "커스텀 확장자는 최대 200개까지 등록할 수 있습니다."
    ),
    CUSTOM_EXTENSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 커스텀 확장자입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
