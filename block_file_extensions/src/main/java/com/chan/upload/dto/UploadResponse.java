package com.chan.upload.dto;

public record UploadResponse(
        Long id,
        String originalFilename,
        long sizeBytes
) {

    public static UploadResponse success(Long id, String originalFilename, long sizeBytes) {
        return new UploadResponse(id, originalFilename, sizeBytes);
    }
}
