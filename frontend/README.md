# FileGuard Frontend

파일 확장자 차단 정책 관리 및 안전한 파일 업로드를 위한 React/Vite 프론트엔드입니다.

## 실행

```bash
npm install
cp .env.example .env
npm run dev
```

기본값은 백엔드 없이 확인 가능한 목업 API 모드입니다. 정책은 브라우저 `localStorage`에 저장됩니다.
백엔드 API 구현 후 `.env`의 `VITE_USE_MOCK_API=false`로 변경하면 실제 API를 호출합니다.

## 화면

- 차단 정책: 고정 확장자 체크/해제, 커스텀 확장자 추가/삭제
- 파일 업로드: 10MB 클라이언트 검사, 정책 경고, 서버 업로드 결과 상태

## API

- `GET /api/policy/fixed-extensions`
- `PATCH /api/policy/fixed-extensions/{extension}`
- `GET /api/policy/custom-extensions`
- `POST /api/policy/custom-extensions`
- `DELETE /api/policy/custom-extensions/{id}`
- `POST /api/upload`
