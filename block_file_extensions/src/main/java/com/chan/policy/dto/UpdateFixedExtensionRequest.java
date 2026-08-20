package com.chan.policy.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFixedExtensionRequest(
        @NotNull Boolean blocked
) {
}
