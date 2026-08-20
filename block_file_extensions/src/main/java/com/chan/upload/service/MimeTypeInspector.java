package com.chan.upload.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Service
public class MimeTypeInspector {

    private static final Logger log = LoggerFactory.getLogger(MimeTypeInspector.class);
    private static final String UNKNOWN_MIME_TYPE = "unknown";

    private final Tika tika = new Tika();

    public void inspect(MultipartFile file, String requestId) {
        String requestedMime = normalize(file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            String detectedMime = normalize(tika.detect(inputStream, file.getOriginalFilename()));

            if (!requestedMime.equals(detectedMime)) {
                log.warn(
                        "MIME_MISMATCH requestId={} filename={} requestedMime={} detectedMime={}",
                        requestId,
                        file.getOriginalFilename(),
                        requestedMime,
                        detectedMime
                );
            }
        } catch (IOException exception) {
            log.warn(
                    "MIME_DETECTION_FAILED requestId={} filename={} requestedMime={}",
                    requestId,
                    file.getOriginalFilename(),
                    requestedMime,
                    exception
            );
        }
    }

    private String normalize(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return UNKNOWN_MIME_TYPE;
        }

        return mimeType.split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
