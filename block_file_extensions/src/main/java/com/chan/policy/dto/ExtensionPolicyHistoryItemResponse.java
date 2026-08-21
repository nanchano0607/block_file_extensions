package com.chan.policy.dto;

import com.chan.policy.domain.ExtensionPolicyAction;
import com.chan.policy.domain.ExtensionPolicyHistory;
import com.chan.policy.domain.ExtensionPolicyType;

import java.time.LocalDateTime;

public record ExtensionPolicyHistoryItemResponse(
        Long id,
        ExtensionPolicyType policyType,
        String extension,
        ExtensionPolicyAction action,
        LocalDateTime changedAt
) {

    public static ExtensionPolicyHistoryItemResponse from(ExtensionPolicyHistory history) {
        return new ExtensionPolicyHistoryItemResponse(
                history.getId(),
                history.getPolicyType(),
                history.getExtension(),
                history.getAction(),
                history.getChangedAt()
        );
    }
}
