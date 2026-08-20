-- V1: 초기 스키마 (spec.md 2장 기준)
-- fixed_extension_policy / custom_extension / upload_file 3개 테이블.
-- extension_policy_history(F3, 선택 구현)는 구현 여부 미확정으로 이번 마이그레이션에서 제외.

CREATE TABLE fixed_extension_policy (
    extension   VARCHAR(20) PRIMARY KEY,
    is_blocked  TINYINT(1) NOT NULL DEFAULT 0,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fixed_extension_policy (extension, is_blocked) VALUES
('bat', 0), ('cmd', 0), ('com', 0), ('cpl', 0), ('exe', 0), ('scr', 0), ('js', 0);

CREATE TABLE custom_extension (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    extension   VARCHAR(20) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custom_extension_extension (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE upload_file (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_filename      VARCHAR(255) NOT NULL,
    stored_filename        VARCHAR(255) NULL,
    extension_candidates   VARCHAR(100) NULL,
    matched_extension      VARCHAR(20) NULL,
    size_bytes             BIGINT NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    block_reason_category  VARCHAR(50) NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_upload_file_created_at (created_at),
    INDEX idx_upload_file_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
