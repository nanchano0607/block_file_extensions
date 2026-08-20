package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ClamAvScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanner.class);
    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 8 * 1024;
    private static final int MAX_RESPONSE_SIZE = 4 * 1024;

    private final String host;
    private final int port;
    private final int timeoutMillis;

    public ClamAvScanner(
            @Value("${app.clamav.host}") String host,
            @Value("${app.clamav.port}") int port,
            @Value("${app.clamav.timeout}") int timeoutMillis
    ) {
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    public void scan(MultipartFile file, String requestId) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);

            try (InputStream fileInputStream = file.getInputStream()) {
                sendFileWithTimeout(socket, fileInputStream);
            }

            handleResponse(readResponse(socket), file, requestId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            log.error(
                    "CLAMAV_SCAN_FAILED requestId={} filename={} host={} port={}",
                    requestId,
                    file.getOriginalFilename(),
                    host,
                    port,
                    exception
            );
            throw new UploadBlockedException(
                    ErrorCode.MALWARE_SCAN_FAILED,
                    BlockReasonCategory.MALWARE_SCAN_FAILED,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private void sendFileWithTimeout(Socket socket, InputStream inputStream) throws IOException {
        // Socket#setSoTimeout only bounds blocking reads, not writes: if clamd stops
        // draining the socket mid-transfer, outputStream.write() can block forever.
        // Running the write on its own thread lets us enforce a real deadline by
        // closing the socket, which unblocks the write with an IOException.
        CompletableFuture<Void> sendCompleted = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                sendFile(socket, inputStream);
                sendCompleted.complete(null);
            } catch (Exception exception) {
                sendCompleted.completeExceptionally(exception);
            }
        });

        try {
            sendCompleted.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            socket.close();
            throw new IOException("Timed out writing file to ClamAV", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to write file to ClamAV", cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing file to ClamAV", exception);
        }
    }

    private void sendFile(Socket socket, InputStream inputStream) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        outputStream.write(INSTREAM_COMMAND);

        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.writeInt(bytesRead);
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.writeInt(0);
        outputStream.flush();
    }

    private String readResponse(Socket socket) throws IOException {
        InputStream inputStream = new BufferedInputStream(socket.getInputStream());
        ByteArrayOutputStream response = new ByteArrayOutputStream();

        while (response.size() < MAX_RESPONSE_SIZE) {
            int value = inputStream.read();
            if (value == -1 || value == 0 || value == '\n') {
                break;
            }
            response.write(value);
        }

        if (response.size() == 0 || response.size() == MAX_RESPONSE_SIZE) {
            throw new IOException("Invalid ClamAV response length");
        }
        return response.toString(StandardCharsets.UTF_8).trim();
    }

    private void handleResponse(String response, MultipartFile file, String requestId) {
        if (response.endsWith("OK")) {
            return;
        }
        if (response.endsWith("FOUND")) {
            log.warn(
                    "CLAMAV_MALWARE_DETECTED requestId={} filename={} response={}",
                    requestId,
                    file.getOriginalFilename(),
                    response
            );
            throw new UploadBlockedException(
                    ErrorCode.MALWARE_DETECTED,
                    BlockReasonCategory.MALWARE_DETECTED,
                    response
            );
        }

        log.error(
                "CLAMAV_INVALID_RESPONSE requestId={} filename={} response={}",
                requestId,
                file.getOriginalFilename(),
                response
        );
        throw new UploadBlockedException(
                ErrorCode.MALWARE_SCAN_FAILED,
                BlockReasonCategory.MALWARE_SCAN_FAILED,
                response
        );
    }
}
