package com.chan.policy.dto;

import com.chan.policy.domain.FixedExtensionPolicy;

public record FixedExtensionResponse(
        String extension,
        boolean blocked
) {

    public static FixedExtensionResponse from(FixedExtensionPolicy policy) {
        return new FixedExtensionResponse(policy.getExtension(), policy.isBlocked());
    }
}
