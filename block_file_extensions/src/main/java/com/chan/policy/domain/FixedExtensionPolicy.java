package com.chan.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_extension_policy")
public class FixedExtensionPolicy {

    @Id
    @Column(name = "extension", length = 20)
    private String extension;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FixedExtensionPolicy() {
    }

    public String getExtension() {
        return extension;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void changeBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}
