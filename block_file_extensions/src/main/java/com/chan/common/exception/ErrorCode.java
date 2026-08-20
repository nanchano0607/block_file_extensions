package com.chan.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    FIXED_EXTENSION_NOT_FOUND(HttpStatus.NOT_FOUND, "정의되지 않은 고정 확장자입니다."),
    INVALID_CUSTOM_EXTENSION_LENGTH(HttpStatus.BAD_REQUEST, "확장자는 1~20자로 입력해주세요."),
    INVALID_CUSTOM_EXTENSION_FORMAT(HttpStatus.BAD_REQUEST, "영문/숫자만 입력 가능합니다."),
    FIXED_EXTENSION_DUPLICATED(HttpStatus.CONFLICT, "이미 고정 차단 목록에 있는 확장자입니다."),
    CUSTOM_EXTENSION_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 확장자입니다."),
    CUSTOM_EXTENSION_LIMIT_EXCEEDED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "커스텀 확장자는 최대 200개까지 등록할 수 있습니다."
    ),
    CUSTOM_EXTENSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 커스텀 확장자입니다."),
    UPLOAD_FILE_SIZE_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "파일 크기 제한을 초과했습니다."),
    UPLOAD_FILE_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "허용되지 않는 파일 형식입니다."),
    UPLOAD_FILE_PROCESSING_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "파일을 처리할 수 없습니다."),
    MALWARE_DETECTED(HttpStatus.UNPROCESSABLE_CONTENT, "악성코드가 탐지되었습니다."),
    MALWARE_SCAN_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
    ),
    UPLOAD_FILE_STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
    ),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
    );

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
