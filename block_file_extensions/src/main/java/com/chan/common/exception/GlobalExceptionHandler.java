package com.chan.common.exception;

import com.chan.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus())
                .body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException() {
        ErrorCode errorCode = ErrorCode.UPLOAD_FILE_SIZE_EXCEEDED;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getMessage()));
    }
}
