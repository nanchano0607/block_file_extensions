package com.chan.upload.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
                        "UPLOAD_STAGE_RESULT requestId={} stage=2 stageName=MIME_TYPE status=WARNING reason=MIME_MISMATCH filename={} requestedMime={} detectedMime={}",
                        requestId,
                        file.getOriginalFilename(),
                        requestedMime,
                        detectedMime
                );
                return;
            }

            log.info(
                    "UPLOAD_STAGE_RESULT requestId={} stage=2 stageName=MIME_TYPE status=PASSED filename={} requestedMime={} detectedMime={}",
                    requestId,
                    file.getOriginalFilename(),
                    requestedMime,
                    detectedMime
            );
        } catch (Exception exception) {
            // MIME은 어떤 경우에도 차단 근거로 쓰지 않는다(로그 전용 단계). Tika의 내부 감지기는
            // 손상된 입력에 대해 IOException이 아닌 RuntimeException을 던지기도 하므로 넓게 잡는다.
            log.warn(
                    "UPLOAD_STAGE_RESULT requestId={} stage=2 stageName=MIME_TYPE status=WARNING reason=MIME_DETECTION_FAILED filename={} requestedMime={}",
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
