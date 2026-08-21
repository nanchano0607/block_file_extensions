package com.chan.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "extension_policy_history")
public class ExtensionPolicyHistory {

    private static final int MAX_EXTENSION_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 10)
    private ExtensionPolicyType policyType;

    @Column(name = "extension", nullable = false, length = MAX_EXTENSION_LENGTH)
    private String extension;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ExtensionPolicyAction action;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    protected ExtensionPolicyHistory() {
    }

    private ExtensionPolicyHistory(
            ExtensionPolicyType policyType,
            String extension,
            ExtensionPolicyAction action
    ) {
        this.policyType = policyType;
        this.extension = validateExtension(extension);
        this.action = action;
    }

    public static ExtensionPolicyHistory fixedPolicyChanged(String extension, boolean blocked) {
        return new ExtensionPolicyHistory(
                ExtensionPolicyType.FIXED,
                extension,
                blocked ? ExtensionPolicyAction.BLOCK_ON : ExtensionPolicyAction.BLOCK_OFF
        );
    }

    public static ExtensionPolicyHistory customExtensionAdded(String extension) {
        return new ExtensionPolicyHistory(
                ExtensionPolicyType.CUSTOM,
                extension,
                ExtensionPolicyAction.ADD
        );
    }

    public static ExtensionPolicyHistory customExtensionDeleted(String extension) {
        return new ExtensionPolicyHistory(
                ExtensionPolicyType.CUSTOM,
                extension,
                ExtensionPolicyAction.DELETE
        );
    }

    private static String validateExtension(String extension) {
        if (extension == null || extension.isBlank() || extension.length() > MAX_EXTENSION_LENGTH) {
            throw new IllegalArgumentException("extension must be between 1 and 20 characters");
        }
        return extension;
    }

    public Long getId() {
        return id;
    }

    public ExtensionPolicyType getPolicyType() {
        return policyType;
    }

    public String getExtension() {
        return extension;
    }

    public ExtensionPolicyAction getAction() {
        return action;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
