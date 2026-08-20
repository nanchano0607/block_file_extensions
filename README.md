# 파일 업로드 확장자 차단 서비스

관리자가 설정한 확장자 차단 정책이 실제 파일 업로드에 강제되는 시스템입니다.
(`bat`/`cmd`/`com`/`cpl`/`exe`/`scr`/`js` 고정 확장자 + 커스텀 확장자 등록, 6단계 서버 검증 파이프라인)

- **배포 URL**: TODO — 배포 완료 후 채워넣기
- **GitHub**: https://github.com/nanchano0607/block_file_extensions

---

## 문서

| 문서 | 내용 |
|---|---|
| [docs/general.md](docs/general.md) | 프로젝트 정의, 범위, 기술 스택 |
| [docs/function.md](docs/function.md) | 화면/기능 단위 상세, 6단계 검증 파이프라인 |
| [docs/spec.md](docs/spec.md) | DB 스키마, API 명세, 검증 로직 상세 |
| [docs/design.md](docs/design.md) | 화면 상태별(로딩/성공/실패) UI 반응 |
| [docs/CONSIDERATIONS.md](docs/CONSIDERATIONS.md) | 기획/보안/예외/운영 관점 판단과 근거 |
| [docs/AI활용&개발기록서.md](docs/AI활용&개발기록서.md) | AI 프롬프트 기록, 사용 도구, 판단 근거 회고 |
| [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md) | 로컬 개발 환경 실행 방법 |
| [docs/DEPLOYMENT_ENV.md](docs/DEPLOYMENT_ENV.md) | 배포 환경변수 관리 |

---

## 기술 스택

| 영역 | 선택 |
|---|---|
| Frontend | React (Vite) |
| Backend | Spring Boot 4.0.7 (Java 21) |
| DB | MySQL 8.x |
| 마이그레이션 | Flyway |
| 악성코드 검사 | ClamAV (Docker, 동기 방식) |
| 배포 | AWS EC2 |

---

## 실행 방법

### 1. 사전 준비

```bash
cp .env.example .env
# .env의 DB_PASSWORD, DB_ROOT_PASSWORD를 로컬용 값으로 변경
```

### 2. 인프라 기동 (MySQL + ClamAV)

```bash
docker compose up -d
docker compose ps
```

> ClamAV는 최초 실행 시 시그니처 DB를 적재하느라 정상 상태가 되기까지 1~3분 정도 걸립니다.
> 자세한 내용은 [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md) 참고.

### 3. 백엔드 실행

```bash
cd block_file_extensions
./mvnw spring-boot:run
```

기동 시 Flyway가 `V1__init.sql` 마이그레이션을 자동 적용해 아래 테이블을 생성합니다.

### 4. 프론트엔드 실행

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

기본값은 `VITE_USE_MOCK_API=true`(백엔드 없이 localStorage 목업으로 동작)입니다.
백엔드와 연동하려면 `frontend/.env`에서 `VITE_USE_MOCK_API=false`로 변경하세요.

---

## Table Schema

전체 DDL은 [block_file_extensions/src/main/resources/db/migration/V1__init.sql](block_file_extensions/src/main/resources/db/migration/V1__init.sql), 컬럼별 설명은 [docs/spec.md](docs/spec.md) 2장 참고.

### fixed_extension_policy — 고정 확장자 정책

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `extension` | `VARCHAR(20)` | PK | 고정 확장자 값 (`bat`,`cmd`,`com`,`cpl`,`exe`,`scr`,`js`) |
| `is_blocked` | `TINYINT(1)` | NOT NULL, DEFAULT 0 | 차단 여부 (기본 unchecked) |
| `updated_at` | `DATETIME` | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 마지막 변경 시각 |

### custom_extension — 커스텀 확장자 정책

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | 내부 식별자 |
| `extension` | `VARCHAR(20)` | NOT NULL, UNIQUE | 커스텀 확장자 값 (소문자 정규화 저장) |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 등록 시각 |

### upload_file — 업로드 이력/결과

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | |
| `original_filename` | `VARCHAR(255)` | NOT NULL | 사용자가 업로드한 원본 파일명 |
| `stored_filename` | `VARCHAR(255)` | NULL | 성공 시 UUID 기반 저장 파일명 |
| `extension_candidates` | `VARCHAR(100)` | NULL | 판별된 확장자 후보 전체 (콤마 구분, 내부 감사용) |
| `matched_extension` | `VARCHAR(20)` | NULL | 실제 차단을 유발한 확장자 1개 (내부 감사용) |
| `size_bytes` | `BIGINT` | NOT NULL | 업로드 파일 크기 |
| `status` | `VARCHAR(20)` | NOT NULL | `SUCCESS` / `BLOCKED` |
| `block_reason_category` | `VARCHAR(50)` | NULL | 차단 시 일반화된 사유 카테고리 |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | |

인덱스: `custom_extension.extension` UNIQUE, `upload_file.created_at` / `upload_file.status`.

---

## 검증 파이프라인 (요약)

업로드된 파일은 아래 순서로 검증되며, 어느 단계에서든 실패하면 즉시 중단됩니다. 상세 로직은 [docs/spec.md](docs/spec.md) 4장 참고.

1. 파일 크기 검사 (10MB)
2. 확장자 검사 (고정 + 커스텀 정책 대조, 이중/다중 확장자 전체 분리 검사)
3. MIME 검사 (로그 전용, 차단 근거로 사용하지 않음)
4. Magic Number 검사 (최종 판단)
5. Parser 구조 검증 (대표 타입만 우선 구현)
6. ClamAV 악성코드 검사 (동기)

---

## 프로젝트 구조

```
.
├── block_file_extensions/   # Spring Boot 백엔드
├── frontend/                 # React (Vite) 프론트엔드
├── docs/                     # 설계 문서, 고려사항, AI 활용 기록
├── docker-compose.yml        # MySQL, ClamAV (로컬 개발용)
└── .env.example
```
