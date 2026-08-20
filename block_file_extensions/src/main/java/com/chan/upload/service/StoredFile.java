package com.chan.upload.service;

import java.nio.file.Path;

public record StoredFile(String filename, Path path) {
}
