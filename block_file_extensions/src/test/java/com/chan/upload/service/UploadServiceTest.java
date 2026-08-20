package com.chan.upload.service;

import com.chan.common.exception.BlockReasonCategory;
import com.chan.common.exception.BusinessException;
import com.chan.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UploadServiceTest {

    private final ExtensionPolicyValidator extensionPolicyValidator = mock(ExtensionPolicyValidator.class);
    private final MimeTypeInspector mimeTypeInspector = mock(MimeTypeInspector.class);
    private final MagicNumberInspector magicNumberInspector = mock(MagicNumberInspector.class);
    private final ParserStructureInspector parserStructureInspector = mock(ParserStructureInspector.class);
    private final ClamAvScanner clamAvScanner = mock(ClamAvScanner.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final UploadFileRecordService uploadFileRecordService = mock(UploadFileRecordService.class);

    private final UploadService uploadService = new UploadService(
            DataSize.ofMegabytes(10),
            extensionPolicyValidator,
            mimeTypeInspector,
            magicNumberInspector,
            parserStructureInspector,
            clamAvScanner,
            fileStorageService,
            uploadFileRecordService
    );

    @Test
    void UploadBlockedException이_아닌_BusinessException도_UNKNOWN_ERROR_카테고리로_기록한다() {
        // 이 업로드 파이프라인의 모든 차단 지점은 UploadBlockedException을 던지므로, 그 외의
        // BusinessException은 특정 단계와 연결지을 근거가 없다. 단계별 ErrorCode -> 카테고리
        // 매핑표를 별도로 유지하는 대신 일반화된 카테고리로 통일한다.
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        BusinessException plainBusinessException = new BusinessException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        willThrow(plainBusinessException).given(extensionPolicyValidator).validate(any(), anyString());

        assertThatThrownBy(() -> uploadService.upload(file)).isSameAs(plainBusinessException);

        verify(uploadFileRecordService).recordBlocked(
                eq("report.pdf"),
                anyLong(),
                eq(List.of("report", "pdf")),
                isNull(),
                eq(BlockReasonCategory.UNKNOWN_ERROR)
        );
    }

    @Test
    void DB_저장_실패처럼_BusinessException이_아닌_예외도_원래_타입_그대로_전파하며_차단_기록과_저장파일_정리를_수행한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        StoredFile storedFile = new StoredFile("uuid-name", Path.of("/tmp/uuid-name"));
        given(fileStorageService.store(file)).willReturn(storedFile);

        DataIntegrityViolationException dbFailure =
                new DataIntegrityViolationException("Data too long for column 'original_filename'");
        given(uploadFileRecordService.recordSuccess(any(), any(), anyLong(), any())).willThrow(dbFailure);

        assertThatThrownBy(() -> uploadService.upload(file)).isSameAs(dbFailure);

        verify(fileStorageService).deleteQuietly(storedFile.path());
        verify(uploadFileRecordService).recordBlocked(
                eq("report.pdf"),
                anyLong(),
                eq(List.of("report", "pdf")),
                isNull(),
                eq(BlockReasonCategory.UNKNOWN_ERROR)
        );
    }
}
