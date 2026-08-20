package com.chan.upload.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum FileSignature {
    WINDOWS_EXECUTABLE(new byte[]{0x4D, 0x5A}, Set.of(), true),
    ELF_EXECUTABLE(new byte[]{0x7F, 0x45, 0x4C, 0x46}, Set.of(), true),
    ZIP(new byte[]{0x50, 0x4B, 0x03, 0x04}, Set.of("zip", "docx", "xlsx", "pptx"), false),
    PDF(new byte[]{0x25, 0x50, 0x44, 0x46}, Set.of("pdf"), false),
    JPG(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, Set.of("jpg", "jpeg"), false),
    PNG(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, Set.of("png"), false),
    GIF(new byte[]{0x47, 0x49, 0x46, 0x38}, Set.of("gif"), false);

    private final byte[] magicNumber;
    private final Set<String> compatibleExtensions;
    private final boolean executable;

    FileSignature(byte[] magicNumber, Set<String> compatibleExtensions, boolean executable) {
        this.magicNumber = magicNumber;
        this.compatibleExtensions = compatibleExtensions;
        this.executable = executable;
    }

    public boolean isAllowedFor(List<String> extensionCandidates) {
        return !executable && extensionCandidates.stream().anyMatch(compatibleExtensions::contains);
    }

    public static Optional<FileSignature> detect(byte[] header) {
        return Arrays.stream(values())
                .filter(signature -> signature.matches(header))
                .findFirst();
    }

    public static int maximumLength() {
        return Arrays.stream(values())
                .mapToInt(signature -> signature.magicNumber.length)
                .max()
                .orElse(0);
    }

    private boolean matches(byte[] header) {
        if (header.length < magicNumber.length) {
            return false;
        }

        for (int index = 0; index < magicNumber.length; index++) {
            if (header[index] != magicNumber[index]) {
                return false;
            }
        }
        return true;
    }
}
