package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClamAvScannerTest {

    private static final String REQUEST_ID = "request-id";

    @Test
    void INSTREAM_프로토콜로_파일을_전송하고_OK이면_통과한다() throws Exception {
        byte[] content = "normal content".getBytes(StandardCharsets.UTF_8);

        try (FakeClamAvServer server = new FakeClamAvServer("stream: OK\0", 0)) {
            ClamAvScanner scanner = scanner(server.port(), 1_000);

            assertThatCode(() -> scanner.scan(file(content), REQUEST_ID)).doesNotThrowAnyException();
            assertThat(server.receivedFile().get(1, TimeUnit.SECONDS)).isEqualTo(content);
        }
    }

    @Test
    void FOUND_응답이면_악성코드로_차단한다() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer(
                "stream: Win.Test.EICAR_HDB-1 FOUND\0",
                0
        )) {
            ClamAvScanner scanner = scanner(server.port(), 1_000);

            assertErrorCode(scanner, ErrorCode.MALWARE_DETECTED);
        }
    }

    @Test
    void 응답_시간을_초과하면_안전하게_업로드를_중단한다() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer("stream: OK\0", 300)) {
            ClamAvScanner scanner = scanner(server.port(), 50);

            assertErrorCode(scanner, ErrorCode.MALWARE_SCAN_FAILED);
        }
    }

    @Test
    void ClamAV에_연결할_수_없으면_안전하게_업로드를_중단한다() throws Exception {
        int unavailablePort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            unavailablePort = serverSocket.getLocalPort();
        }

        assertErrorCode(scanner(unavailablePort, 100), ErrorCode.MALWARE_SCAN_FAILED);
    }

    private void assertErrorCode(ClamAvScanner scanner, ErrorCode expected) {
        assertThatThrownBy(() -> scanner.scan(file("content".getBytes(StandardCharsets.UTF_8)), REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private ClamAvScanner scanner(int port, int timeoutMillis) {
        return new ClamAvScanner("127.0.0.1", port, timeoutMillis);
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "sample.txt", "text/plain", content);
    }

    private static final class FakeClamAvServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final CompletableFuture<byte[]> receivedFile;

        private FakeClamAvServer(String response, long responseDelayMillis) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.receivedFile = CompletableFuture.supplyAsync(() -> handle(response, responseDelayMillis));
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private CompletableFuture<byte[]> receivedFile() {
            return receivedFile;
        }

        private byte[] handle(String response, long responseDelayMillis) {
            try (Socket socket = serverSocket.accept()) {
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                assertCommand(inputStream);

                ByteArrayOutputStream file = new ByteArrayOutputStream();
                int chunkLength;
                while ((chunkLength = inputStream.readInt()) != 0) {
                    file.write(inputStream.readNBytes(chunkLength));
                }

                if (responseDelayMillis > 0) {
                    Thread.sleep(responseDelayMillis);
                }
                socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                return file.toByteArray();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        private void assertCommand(DataInputStream inputStream) throws IOException {
            byte[] command = inputStream.readNBytes("zINSTREAM\0".length());
            if (!new String(command, StandardCharsets.US_ASCII).equals("zINSTREAM\0")) {
                throw new IOException("Unexpected ClamAV command");
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            try {
                receivedFile.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // The timeout test intentionally closes the client before the fake server responds.
            }
        }
    }
}
