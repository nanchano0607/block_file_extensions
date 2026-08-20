package com.chan.upload.service;

import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserStructureInspectorTest {

    private static final String REQUEST_ID = "request-id";

    private final ParserStructureInspector inspector = new ParserStructureInspector();

    @Test
    void 유효한_docx_구조는_통과한다() throws Exception {
        MockMultipartFile file = docxFile(validDocxBytes());

        assertThatCode(() -> inspector.inspect(file, List.of("docx"), REQUEST_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void 손상된_docx_구조는_차단한다() {
        MockMultipartFile file = docxFile("not a real docx".getBytes());

        assertThatThrownBy(() -> inspector.inspect(file, List.of("docx"), REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_PROCESSING_FAILED);
    }

    @Test
    void 앞선_후보가_통과해도_뒤에_오는_후보의_구조가_손상되면_차단한다() throws Exception {
        // invoice.jpg.docx 처럼, 실제로는 유효한 jpg 바이트를 docx로도 위장한 폴리글랏 시나리오.
        // 앞 후보(jpg)만 검사하고 멈추면 뒤에 숨은 손상된 docx 구조를 놓치게 된다.
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.jpg.docx", "image/jpeg", validJpgBytes()
        );

        assertThatThrownBy(() -> inspector.inspect(file, List.of("jpg", "docx"), REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FILE_PROCESSING_FAILED);
    }

    private MockMultipartFile docxFile(byte[] content) {
        return new MockMultipartFile(
                "file",
                "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content
        );
    }

    private byte[] validDocxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] validJpgBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", outputStream);
            return outputStream.toByteArray();
        }
    }
}
