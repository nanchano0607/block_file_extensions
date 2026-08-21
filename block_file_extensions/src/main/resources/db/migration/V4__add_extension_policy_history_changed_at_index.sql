-- V4: GET /api/policy/history는 changed_at DESC, id DESC로 정렬 조회하는데(ExtensionPolicyHistoryRepository),
-- 기존에는 extension에만 인덱스가 있어 append-only로 계속 커지는 이 테이블에서 매 페이지 조회마다
-- 전체 테이블 스캔 + filesort가 발생한다. 정렬에 쓰이는 컬럼을 커버하는 인덱스를 추가한다.
ALTER TABLE extension_policy_history
    ADD INDEX idx_history_changed_at (changed_at DESC, id DESC);
