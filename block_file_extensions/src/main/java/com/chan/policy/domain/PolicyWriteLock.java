package com.chan.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "policy_write_lock")
public class PolicyWriteLock {

    public static final String CUSTOM_EXTENSION_LIMIT = "CUSTOM_EXTENSION_LIMIT";

    @Id
    @Column(name = "lock_name", length = 50)
    private String name;

    protected PolicyWriteLock() {
    }

    public String getName() {
        return name;
    }
}
