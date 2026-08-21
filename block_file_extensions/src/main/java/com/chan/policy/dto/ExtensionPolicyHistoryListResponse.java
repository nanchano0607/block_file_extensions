package com.chan.policy.dto;

import java.util.List;

public record ExtensionPolicyHistoryListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext,
        List<ExtensionPolicyHistoryItemResponse> items
) {
}
