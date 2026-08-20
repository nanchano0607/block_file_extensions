package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final String OWNER_READ_WRITE_ONLY = "rw-------";

    private final Path uploadDirectory;

    public FileStorageService(@Value("${app.upload.directory}") Path uploadDirectory) {
        this.uploadDirectory = uploadDirectory.toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        String storedFilename = UUID.randomUUID().toString();
        Path target = uploadDirectory.resolve(storedFilename);

        try {
            Files.createDirectories(uploadDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            Files.setPosixFilePermissions(
                    target,
                    PosixFilePermissions.fromString(OWNER_READ_WRITE_ONLY)
            );
            return new StoredFile(storedFilename, target);
        } catch (IOException | UnsupportedOperationException exception) {
            deleteQuietly(target);
            throw new UploadBlockedException(
                    ErrorCode.UPLOAD_FILE_STORAGE_FAILED,
                    BlockReasonCategory.STORAGE_FAILED,
                    exception.getMessage()
            );
        }
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original storage or database exception remains the primary failure.
        }
    }
}
