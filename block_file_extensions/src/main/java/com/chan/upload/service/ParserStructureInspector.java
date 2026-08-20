package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.ErrorCode;
import com.chan.common.exception.UploadBlockedException;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ParserStructureInspector {

    private static final Logger log = LoggerFactory.getLogger(ParserStructureInspector.class);

    public void inspect(MultipartFile file, List<String> extensionCandidates, String requestId) {
        for (String extension : extensionCandidates) {
            if (supports(extension)) {
                inspect(file, extension, requestId);
                return;
            }
        }
    }

    private boolean supports(String extension) {
        return switch (extension) {
            case "docx", "xlsx", "pptx", "jpg", "jpeg", "png", "gif" -> true;
            default -> false;
        };
    }

    private void inspect(MultipartFile file, String extension, String requestId) {
        try {
            switch (extension) {
                case "docx" -> inspectDocx(file);
                case "xlsx" -> inspectXlsx(file);
                case "pptx" -> inspectPptx(file);
                case "jpg", "jpeg", "png", "gif" -> inspectImage(file);
                default -> throw new IllegalArgumentException("Unsupported parser extension: " + extension);
            }
        } catch (Exception exception) {
            log.warn(
                    "PARSER_STRUCTURE_BLOCKED requestId={} filename={} parserType={}",
                    requestId,
                    file.getOriginalFilename(),
                    extension,
                    exception
            );
            throw new UploadBlockedException(
                    ErrorCode.UPLOAD_FILE_PROCESSING_FAILED,
                    BlockReasonCategory.PARSER_STRUCTURE_INVALID,
                    "parserType=" + extension + ", cause=" + exception.getClass().getSimpleName()
            );
        }
    }

    private void inspectDocx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument ignored = new XWPFDocument(inputStream)) {
            // Opening the document validates its OOXML structure.
        }
    }

    private void inspectXlsx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XSSFWorkbook ignored = new XSSFWorkbook(inputStream)) {
            // Opening the workbook validates its OOXML structure.
        }
    }

    private void inspectPptx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XMLSlideShow ignored = new XMLSlideShow(inputStream)) {
            // Opening the slide show validates its OOXML structure.
        }
    }

    private void inspectImage(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            if (ImageIO.read(inputStream) == null) {
                throw new IOException("ImageIO could not decode the image");
            }
        }
    }
}
