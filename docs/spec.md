# spec.md — DB 스키마 / API 명세 / 검증 규칙 상세

> 본 문서는 `general.md`(범위/기술스택)와 `function.md`(기능 정의)에서 확정된 내용을 실제 구현 가능한 수준으로 구체화한다.
> 화면 상태별 UI(로딩/에러 표시 등)는 `design.md`에서 다룬다.

---

## 1. 개요

| 항목 | 내용 |
|---|---|
| DB | MySQL 8.x |
| 문자셋 | `utf8mb4` (유니코드 확장자/파일명 대응) |
| ORM | Spring Data JPA (Spring 4.0.7) |
| 시간 저장 | `DATETIME` (UTC 기준 저장, 표시 시점에 타임존 변환은 프론트 책임) |

---

## 2. DB 스키마

### 2-1. ERD 개요 (텍스트)

```
fixed_extension_policy          custom_extension              upload_file
─────────────────────           ─────────────────             ─────────────────
extension (PK)                  id (PK)                       id (PK)
is_blocked                      extension (UNIQUE)             original_filename
updated_at                      created_at                     stored_filename
                                                                 extension_candidates
                                                                 matched_extension
                                                                 size_bytes
                                                                 status
                                                                 block_reason_category
                                                                 created_at
(선택) extension_policy_history
─────────────────────────────
id (PK)
policy_type   (FIXED / CUSTOM)
extension
action        (BLOCK_ON / BLOCK_OFF / ADD / DELETE)
changed_at
```

세 테이블(`fixed_extension_policy`, `custom_extension`, `upload_file`)은 서로 FK로 직접 연결하지 않는다. 정책 테이블은 "현재 차단 목록"이라는 스냅샷 데이터이고, `upload_file`은 업로드 이력이므로 시점에 따라 정책이 바뀔 수 있어 느슨하게 분리한다 (업로드 당시 어떤 확장자였는지는 `upload_file.extension`에 값 자체로 남긴다).

### 2-2. `fixed_extension_policy` (고정 확장자 정책)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `extension` | `VARCHAR(20)` | PK | 고정 확장자 값 (`bat`,`cmd`,`com`,`cpl`,`exe`,`scr`,`js`) — 7개 행으로 시드(seed) 데이터 고정, 애플리케이션에서 추가/삭제 불가 |
| `is_blocked` | `TINYINT(1)` | NOT NULL, DEFAULT 0 | 차단 여부 (0 = unchecked/허용, 1 = checked/차단) — **default unchecked 요구사항 반영** |
| `updated_at` | `DATETIME` | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 마지막 변경 시각 |

```sql
CREATE TABLE fixed_extension_policy (
    extension   VARCHAR(20) PRIMARY KEY,
    is_blocked  TINYINT(1) NOT NULL DEFAULT 0,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fixed_extension_policy (extension, is_blocked) VALUES
('bat', 0), ('cmd', 0), ('com', 0), ('cpl', 0), ('exe', 0), ('scr', 0), ('js', 0);
```

> 7개 행은 애플리케이션 시작 시 존재를 보장(seed/migration)해야 하며, 프론트에서 목록을 하드코딩하지 않고 이 테이블을 항상 조회해서 렌더링한다.

### 2-3. `custom_extension` (커스텀 확장자 정책)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | 내부 식별자 (삭제 API의 `{id}`로 사용) |
| `extension` | `VARCHAR(20)` | NOT NULL, **UNIQUE** | 커스텀 확장자 값, 소문자로 정규화하여 저장 (대소문자 무관 중복 방지) |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 등록 시각 |

```sql
CREATE TABLE custom_extension (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    extension   VARCHAR(20) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custom_extension_extension (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**제약/규칙**
- `UNIQUE KEY`로 **DB 레벨에서 중복 등록을 최종 방어**한다 (애플리케이션 검증이 뚫리더라도 DB가 막음). 동시 요청으로 인한 레이스 컨디션 대비.
- 최대 200개 제한은 DB 제약(트리거 등)으로 걸지 않고 **애플리케이션 레벨에서 INSERT 전 `COUNT(*)` 체크**로 처리한다. 이유: 200이라는 값은 운영상 변경될 수 있는 정책값이라 DB 스키마에 하드코딩하지 않는 것이 유지보수에 유리하며, 200개 수준에서는 `COUNT(*)` 비용이 무시할 만하다.
- 고정 확장자 7개와의 중복 방지는 DB 제약이 아닌 **애플리케이션 로직에서 INSERT 전 고정 목록과 대조**한다 (두 테이블이 분리되어 있어 DB CHECK 제약으로 표현하기 어려움).

### 2-4. `upload_file` (업로드 이력/결과)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | |
| `original_filename` | `VARCHAR(255)` | NOT NULL | 사용자가 업로드한 원본 파일명 (저장 경로에는 쓰지 않고 기록용) |
| `stored_filename` | `VARCHAR(255)` | NULL 허용 | 성공 시 실제 저장된 UUID 파일명. 차단된 요청은 저장하지 않으므로 NULL |
| `extension_candidates` | `VARCHAR(100)` | NULL 허용 | 1단계 판별 알고리즘으로 `.` 분리 후 빈 문자열을 제거하고 남은 **모든 확장자 후보를 콤마(`,`)로 이어붙인 값**. 예: `file.exe.txt` → `"exe,txt"`. 확장자 없는 파일은 NULL |
| `matched_extension` | `VARCHAR(20)` | NULL 허용 | 위 후보들 중 **정책과 실제로 매칭되어 차단을 유발한 확장자 1개**. 여러 후보가 동시에 매칭되어도 최초로 매칭된 값 하나만 저장. 차단되지 않은 요청은 NULL (내부 감사/로그 전용 컬럼이며, **사용자 응답에는 절대 노출하지 않는다** — F2-2/F2-3의 메시지 노출 원칙 참고) |
| `size_bytes` | `BIGINT` | NOT NULL | 업로드 시도 파일 크기 |
| `status` | `VARCHAR(20)` | NOT NULL | `SUCCESS` / `BLOCKED` |
| `block_reason_category` | `VARCHAR(50)` | NULL 허용 | 차단된 경우의 **일반화된 카테고리**만 저장 (예: `SIZE_EXCEEDED`, `EXTENSION_BLOCKED`, `MALWARE_DETECTED` 등). 세부 판단 근거(어떤 시그니처 불일치 등)는 별도 애플리케이션 로그에만 남기고 이 테이블에는 남기지 않는다 (DB가 유출되어도 검증 로직 세부가 노출되지 않도록) |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |

```sql
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
```

> **왜 단일 `extension` 컬럼이 아닌 `extension_candidates` + `matched_extension` 2개로 분리했는가**: `file.exe.txt`처럼 이중/다중 확장자는 판별 알고리즘상 여러 개의 후보(`exe`, `txt`)를 동시에 갖는다. 이걸 컬럼 하나에 억지로 하나만 담으면(예: 마지막 확장자만, 혹은 첫 확장자만) 실제 차단 판단에 쓰인 값과 저장된 값이 달라질 수 있어 감사/추적 목적에서 정보 손실이 생긴다. 그래서 **① 판별에 사용된 후보 전체는 `extension_candidates`에 원본 그대로 보존**하고, **② 실제로 정책에 매칭되어 차단을 유발한 값은 `matched_extension`에 별도로 남긴다.** 둘 다 서버 내부용(로그·감사 목적)이며, 사용자에게 반환하는 응답 메시지에는 포함하지 않는다(F2-2/F2-3 메시지 노출 원칙과 동일 기준 적용).

> 이 테이블은 과제 최소 요구사항(A, B)에는 없지만, F2-3(업로드 결과 응답)의 이력을 남기고 **운영 관점 고려사항("로그/모니터링 관점에서 무엇을 남길 것인가")**에 대한 답으로 추가했다. 시간이 부족하면 이 테이블 없이 애플리케이션 로그(파일/CloudWatch 등)로만 대체 가능 — `CONSIDERATIONS.md`에 트레이드오프 기록.

### 2-5. (선택)`extension_policy_history` — 정책 변경 이력 (F3-1)

```sql
CREATE TABLE extension_policy_history (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_type  VARCHAR(10) NOT NULL,   -- 'FIXED' | 'CUSTOM'
    extension    VARCHAR(20) NOT NULL,
    action       VARCHAR(20) NOT NULL,   -- 'BLOCK_ON' | 'BLOCK_OFF' | 'ADD' | 'DELETE'
    changed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_history_extension (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- F1-2/F1-4/F1-5에서 정책이 바뀔 때마다 이 테이블에 이력 1행을 추가하는 방식(감사 로그). 단일 관리자 가정이므로 "누가"는 생략, 추후 다중 사용자 확장 시 `changed_by` 컬럼 추가.
- 구현 우선순위가 낮으므로(선택 범위), 미구현 시 `CONSIDERATIONS.md`에 "필요성 인지, 시간 관계상 미구현" 근거만 남긴다.

---

## 3. API 명세

### 3-1. 공통 응답 규격

```json
{
  "success": true,
  "message": "string",
  "data": {}
}
```

- 성공: `success: true`, `data`에 실제 값
- 실패: `success: false`, `message`에 **일반화된 사유(F2-2 "사용자 노출 메시지" 수준)**, `data`는 `null`
- HTTP 상태 코드와 함께 사용 (2xx/4xx/5xx는 아래 각 API 별 표기)

### 3-2. 정책 관리 API

#### `GET /api/policy/fixed-extensions`
고정 확장자 7개와 차단 여부 조회 (F1-1)

응답 예시
```json
{
  "success": true,
  "message": "조회되었습니다.",
  "data": [
    { "extension": "bat", "blocked": false },
    { "extension": "exe", "blocked": true }
  ]
}
```

#### `PATCH /api/policy/fixed-extensions/{extension}`
고정 확장자 체크/해제 (F1-2)

요청
```json
{ "blocked": true }
```
- `{extension}`이 사전 정의된 7개 값이 아니면 `404`
- 성공 시 `200`, 실패(DB 오류 등) 시 `500`

#### `GET /api/policy/custom-extensions`
커스텀 확장자 목록 + 개수 조회 (F1-3)

응답 예시
```json
{
  "success": true,
  "message": "조회되었습니다.",
  "data": { "count": 3, "limit": 200, "items": [
    { "id": 1, "extension": "sh" },
    { "id": 2, "extension": "ps1" }
  ]}
}
```

#### `POST /api/policy/custom-extensions`
커스텀 확장자 추가 (F1-4)

요청
```json
{ "extension": "sh" }
```

검증 순서 (실패 시 즉시 반환, `4xx`)
1. 빈 값 / 20자 초과 → `400`, `"확장자는 1~20자로 입력해주세요."`
2. 정규화 후 형식 위반(허용 문자 외 포함) → `400`, `"영문/숫자만 입력 가능합니다."`
3. 고정 확장자 7개와 중복 → `409`, `"이미 고정 차단 목록에 있는 확장자입니다."`
4. 커스텀 목록 내 중복 (대소문자 무관) → `409`, `"이미 등록된 확장자입니다."`
5. 200개 초과 → `422`, `"커스텀 확장자는 최대 200개까지 등록할 수 있습니다."`
6. 통과 시 `201`, 등록된 항목 반환

#### `DELETE /api/policy/custom-extensions/{id}`
커스텀 확장자 삭제 (F1-5)
- 존재하지 않는 `id` → `404`
- 성공 시 `200`

#### (선택) `GET /api/policy/history`
정책 변경 이력 조회 (F3-1), 페이지네이션 파라미터 `page`, `size` 지원 권장

### 3-3. 업로드 API

#### `POST /api/upload`
파일 업로드 (F2-1~F2-3), `multipart/form-data`, 필드명 `file`

**성공 응답** (`200`)
```json
{
  "success": true,
  "message": "업로드에 성공했습니다.",
  "data": { "id": 10, "originalFilename": "report.pdf", "sizeBytes": 102400 }
}
```

**차단 응답** (`422`, 정책/검증 위반 — 5xx가 아닌 4xx 계열로 통일해 "요청 자체는 정상적으로 처리했으나 정책상 거부"임을 표현)
```json
{
  "success": false,
  "message": "허용되지 않는 파일 형식입니다.",
  "data": null
}
```
- `message`는 F2-2 표의 카테고리 문구만 사용하며, 어떤 단계에서 왜 막혔는지는 절대 포함하지 않는다.
- 서버 내부 로그에는 `요청ID, 파일명, 파일크기, 차단단계, 상세사유`를 남기되 응답 바디에는 넣지 않는다.

**서버 오류** (`500`): `"일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."`

---

## 4. 검증 규칙 상세 (F2-2 6단계 구현 로직)

### 4-0. 파일 크기 검사
- 기준: **10MB (10 * 1024 * 1024 bytes)**
- Spring 설정: `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=10MB`로 1차 방어(설정 초과 시 Spring이 자체적으로 예외 발생) + 컨트롤러/서비스 레벨에서 실제 바이트 수 재확인(설정 우회 대비 이중 검증)

### 4-1. 확장자 판별 알고리즘 (function.md F2-2 규칙 그대로 구현)
1. 파일명에 `.`이 하나도 없으면 → 확장자 없는 파일로 즉시 차단
2. `.` 기준 전체 분리 → 빈 문자열 제거
3. 남은 조각 없음 → 차단
4. 남은 조각 각각을 소문자로 정규화 후 정책(고정 7개 `is_blocked=true` + 커스텀 전체)과 대조, 하나라도 매칭되면 차단

**정규화 규칙**
- 대소문자 무관: 비교 전 항상 소문자로 변환 (`file.PDF` → `pdf`로 비교)
- 앞뒤 공백 제거(trim)
- 유니코드 확장자(예: 전각문자)는 일반 확장자와 동일하게 문자열 비교만 수행 (별도 차단 목록에 없으면 통과) — 별도의 특별 처리를 하지 않는 이유: 정책 등록 자체가 유니코드 입력을 지금 단계에서는 막지 않으므로, 등록되지 않은 유니코드 확장자를 임의로 차단하는 것은 과잉 차단이라 판단

**이중/다중 확장자 저장 규칙 (`file.exe.txt` 등)**
- 판별 과정에서 나온 확장자 후보 **전체**를 `upload_file.extension_candidates`에 콤마 구분 문자열로 저장한다 (예: `"exe,txt"`). 원래 파일명에 등장한 순서를 그대로 유지한다.
- 여러 후보 중 정책에 매칭되어 실제로 차단을 유발한 값이 있다면 `upload_file.matched_extension`에 그 값 하나만 저장한다 (여러 후보가 동시에 매칭되더라도 검사 로직상 가장 먼저 매칭된 후보 하나만 기록 — 순차 검사이므로 자연스럽게 첫 매칭에서 즉시 중단됨).
- 차단되지 않고 통과된 업로드는 `matched_extension`이 NULL이며, `extension_candidates`에는 후보 전체가 참고용으로 남는다.
- 이 두 컬럼은 **내부 로그/감사 전용**이며, F2-3 API 응답(`message`)에는 노출하지 않는다.

### 4-2. MIME 검사 (로그 전용, 차단 없음)
- 요청 `Content-Type` 헤더 값과, 1단계에서 추출한 확장자로부터 기대되는 MIME(내부 매핑 테이블)을 비교
- 불일치 시 애플리케이션 로그에 `WARN` 레벨로 `요청ID, 파일명, 요청MIME, 기대MIME` 기록만 하고 처리는 계속 진행

### 4-3. Magic Number 검사 (최종 판단)
파일의 앞부분 바이트를 읽어 실제 시그니처와 대조. 확장자 정책이 차단 목록에 없어 1단계를 통과했더라도, **내용이 위험한 실행 파일 시그니처와 일치하면 이 단계에서 차단**한다.

| 유형 | 매직 넘버(Hex, 앞부분) |
|---|---|
| Windows 실행파일 (EXE/DLL 등) | `4D 5A` |
| ELF 실행파일 | `7F 45 4C 46` |
| ZIP 계열(docx/xlsx/pptx/zip) | `50 4B 03 04` |
| PDF | `25 50 44 46` |
| JPG | `FF D8 FF` |
| PNG | `89 50 4E 47` |
| GIF | `47 49 46 38` |

> `js`, `sh`, `bat` 등 텍스트 기반 스크립트 파일은 고유한 매직 넘버가 없다는 한계가 있다 — 이 경우 1단계(확장자)가 사실상 1차 방어선이 되며, 매직넘버 검사는 "바이너리 실행파일이 문서 확장자로 위장한 경우"를 잡아내는 데 주된 효용이 있다. 이 한계는 `CONSIDERATIONS.md`에 명시한다.

### 4-4. Parser 구조 검증 (대표 타입 한정)
- 대상: ZIP 기반 오피스 문서(docx/xlsx/pptx) — Apache POI로 실제 열기 시도, 이미지 파일(jpg/jpeg/png/gif) — `ImageIO.read()`로 디코딩 시도
- 실패(예외 발생/디코딩 불가) 시 차단
- 그 외 확장자는 이 단계를 스킵(패스)하고 다음 단계로 진행 — 전체 확장자 구현은 비용 대비 효율이 낮다고 general.md에서 이미 결정

### 4-5. ClamAV 악성코드 검사 (동기)
- ClamAV를 Docker 컨테이너로 EC2에 함께 구동 (`clamd` 데몬, 기본 포트 `3310`)
- Spring 애플리케이션에서 `INSTREAM` 프로토콜로 파일 스트림을 clamd에 전달해 스캔 (예: `fscan`/`clamav-client` 계열 라이브러리 또는 직접 소켓 통신 구현)
- 스캔 결과 `FOUND`(감염) 시 즉시 차단, `OK`(정상) 시 통과
- 타임아웃 설정 필요(예: 10초) — 응답 없음도 하나의 실패 케이스로 간주해 차단 처리(가용성보다 안전 우선)

---

## 5. 파일 저장 규칙

- 저장 경로: EC2 로컬 디스크, 애플리케이션 정적 리소스 경로 밖 (예: `/home/ec2-user/uploads/`)
- 저장 파일명: `UUID.randomUUID()` 기반 (원본 파일명 사용 금지 — 경로 조작/충돌 방지)
- 저장 시 실행 권한 제거: 저장 직후 `chmod 600` 적용 (Linux 파일 시스템 레벨에서 실행 방지 이중 방어). 다운로드 기능 추가 시 `640`으로 재검토 필요
- 원본 파일명은 `upload_file.original_filename`에만 기록하고 실제 파일시스템 경로에는 사용하지 않음

---

## 6. 커스텀 확장자 입력 정규화 규칙 (F1-4 상세)

| 단계 | 규칙 |
|---|---|
| 1 | 앞뒤 공백 제거 |
| 2 | 선행 `.` 제거 (사용자가 `.sh` 입력해도 `sh`로 정규화) |
| 3 | 소문자로 변환 |
| 4 | 허용 문자: 영문 소문자(`a-z`) + 숫자(`0-9`)만 허용, 그 외(특수문자/공백/유니코드) 포함 시 `400` 거부 |
| 5 | 길이 1~20자 |

> 정책 파일 검증(4-1)의 확장자 후보 비교도 동일한 정규화(소문자 변환)를 거친 뒤 비교한다 — 등록 시점과 판별 시점의 정규화 규칙을 반드시 일치시켜야 정합성이 깨지지 않는다.

---

## 7. 성능/인덱스 고려사항

- `custom_extension.extension`에 UNIQUE 인덱스 → 중복 체크(`SELECT` 또는 INSERT 시 제약 위반)와 업로드 시 매칭 조회(`WHERE extension IN (...)`) 모두 이 인덱스로 커버됨
- 최대 200행 규모에서는 인덱스 없이도 성능 문제가 없는 수준이지만, 업로드마다 매칭 조회가 발생하므로 인덱스를 명시적으로 건다
- `upload_file`은 시간이 지나며 행 수가 계속 늘어나는 테이블이므로 `created_at`, `status`에 인덱스 부여 (이력 조회/모니터링 대비)

---

## 8. 아직 결정되지 않은 사항 (design.md 또는 추가 논의 필요)

| 항목 | 현재 상태 |
|---|---|
| F3(정책 변경 이력) 구현 여부 | 스키마만 준비, 실제 구현은 시간에 따라 결정 |
