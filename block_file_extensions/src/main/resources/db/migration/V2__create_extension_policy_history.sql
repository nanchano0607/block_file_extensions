-- V2: 확장자 정책 변경 이력(F3-1)
-- 단일 관리자를 가정하므로 changed_by는 두지 않고, 정책 종류·확장자·변경 행위만 보존한다.

CREATE TABLE extension_policy_history (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_type  VARCHAR(10) NOT NULL,
    extension    VARCHAR(20) NOT NULL,
    action       VARCHAR(20) NOT NULL,
    changed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_history_extension (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
