package com.chan.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "custom_extension")
public class CustomExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "extension", nullable = false, unique = true, length = 20)
    private String extension;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CustomExtension() {
    }

    public CustomExtension(String extension) {
        this.extension = extension;
    }

    public Long getId() {
        return id;
    }

    public String getExtension() {
        return extension;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
