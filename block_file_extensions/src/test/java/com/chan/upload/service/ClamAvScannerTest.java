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

    @Test
    void clamd가_연결만_받고_읽지_않으면_쓰기_타임아웃으로_안전하게_업로드를_중단한다() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            // clamd가 커넥션은 받았지만 응답 없이 멈춘 상황(과부하 등)을 재현: 소켓을 절대 읽지 않는다.
            CompletableFuture<Void> stalledServer = CompletableFuture.runAsync(() -> {
                try (Socket ignored = serverSocket.accept()) {
                    Thread.sleep(2_000);
                } catch (Exception ignored) {
                    // 테스트 종료 시 서버소켓이 닫히며 발생하는 예외는 무시한다.
                }
            });

            ClamAvScanner scanner = scanner(port, 200);
            // 소켓 송신/수신 버퍼를 확실히 넘겨 write()가 실제로 블로킹되도록 충분히 큰 페이로드를 사용한다.
            byte[] largePayload = new byte[8 * 1024 * 1024];

            long start = System.currentTimeMillis();
            assertErrorCode(scanner, ErrorCode.MALWARE_SCAN_FAILED, largePayload);
            long elapsedMillis = System.currentTimeMillis() - start;

            assertThat(elapsedMillis).isLessThan(2_000);
            stalledServer.cancel(true);
        }
    }

    private void assertErrorCode(ClamAvScanner scanner, ErrorCode expected) {
        assertErrorCode(scanner, expected, "content".getBytes(StandardCharsets.UTF_8));
    }

    private void assertErrorCode(ClamAvScanner scanner, ErrorCode expected, byte[] content) {
        assertThatThrownBy(() -> scanner.scan(file(content), REQUEST_ID))
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
