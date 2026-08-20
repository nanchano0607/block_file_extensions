package com.chan.upload.domain;

import com.chan.common.exception.BlockReasonCategory;

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
@Table(name = "upload_file")
public class UploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", length = 255)
    private String storedFilename;

    @Column(name = "extension_candidates", length = 100)
    private String extensionCandidates;

    @Column(name = "matched_extension", length = 20)
    private String matchedExtension;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UploadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_reason_category", length = 50)
    private BlockReasonCategory blockReasonCategory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UploadFile() {
    }

    private UploadFile(
            String originalFilename,
            String storedFilename,
            String extensionCandidates,
            String matchedExtension,
            long sizeBytes,
            UploadStatus status,
            BlockReasonCategory blockReasonCategory
    ) {
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.extensionCandidates = extensionCandidates;
        this.matchedExtension = matchedExtension;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.blockReasonCategory = blockReasonCategory;
    }

    public static UploadFile success(
            String originalFilename,
            String storedFilename,
            String extensionCandidates,
            long sizeBytes
    ) {
        return new UploadFile(
                originalFilename,
                storedFilename,
                extensionCandidates,
                null,
                sizeBytes,
                UploadStatus.SUCCESS,
                null
        );
    }

    public static UploadFile blocked(
            String originalFilename,
            String extensionCandidates,
            String matchedExtension,
            long sizeBytes,
            BlockReasonCategory blockReasonCategory
    ) {
        return new UploadFile(
                originalFilename,
                null,
                extensionCandidates,
                matchedExtension,
                sizeBytes,
                UploadStatus.BLOCKED,
                blockReasonCategory
        );
    }

    public Long getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public String getExtensionCandidates() {
        return extensionCandidates;
    }

    public String getMatchedExtension() {
        return matchedExtension;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public BlockReasonCategory getBlockReasonCategory() {
        return blockReasonCategory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
