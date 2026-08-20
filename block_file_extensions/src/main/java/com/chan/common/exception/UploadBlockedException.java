package com.chan.common.exception;

public class UploadBlockedException extends BusinessException {

    private final BlockReasonCategory category;
    private final String detail;
    private final String matchedExtension;

    public UploadBlockedException(
            ErrorCode errorCode,
            BlockReasonCategory category,
            String detail
    ) {
        this(errorCode, category, detail, null);
    }

    public UploadBlockedException(
            ErrorCode errorCode,
            BlockReasonCategory category,
            String detail,
            String matchedExtension
    ) {
        super(errorCode);
        this.category = category;
        this.detail = detail;
        this.matchedExtension = matchedExtension;
    }

    public BlockReasonCategory getCategory() {
        return category;
    }

    public String getDetail() {
        return detail;
    }

    public String getMatchedExtension() {
        return matchedExtension;
    }
}
