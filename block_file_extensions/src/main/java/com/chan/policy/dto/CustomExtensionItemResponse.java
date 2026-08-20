package com.chan.policy.dto;

import com.chan.policy.domain.CustomExtension;

public record CustomExtensionItemResponse(
        Long id,
        String extension
) {

    public static CustomExtensionItemResponse from(CustomExtension customExtension) {
        return new CustomExtensionItemResponse(
                customExtension.getId(),
                customExtension.getExtension()
        );
    }
}
