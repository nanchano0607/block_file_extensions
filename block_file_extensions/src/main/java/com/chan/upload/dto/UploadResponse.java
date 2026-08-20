package com.chan.upload.dto;

public record UploadResponse(
        Long id,
        String originalFilename,
        long sizeBytes
) {

    public static UploadResponse pending(String originalFilename, long sizeBytes) {
        return new UploadResponse(null, originalFilename, sizeBytes);
    }
}
