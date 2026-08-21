-- V3: 커스텀 확장자 200개 한도의 count/insert를 DB 단위로 직렬화하기 위한 락 행.
-- 여러 애플리케이션 인스턴스가 동일한 MySQL을 사용해도 SELECT ... FOR UPDATE로 동시 등록을 제어한다.

CREATE TABLE policy_write_lock (
    lock_name VARCHAR(50) PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO policy_write_lock (lock_name) VALUES ('CUSTOM_EXTENSION_LIMIT');
