package com.chan.policy.dto;

import java.util.List;

public record CustomExtensionListResponse(
        int count,
        int limit,
        List<CustomExtensionItemResponse> items
) {
}
