# LOCAL_DEVELOPMENT.md — 로컬 개발 환경

로컬에서는 MySQL과 ClamAV만 Docker Compose로 띄우고, 백엔드/프론트엔드는 각자 `mvnw`/`npm run dev`로 직접 실행한다 (배포용 컨테이너 빌드는 [DEPLOYMENT.md](DEPLOYMENT.md) 참고).

## 1. 인프라 (MySQL, ClamAV)

```bash
cp .env.example .env
# .env의 DB_PASSWORD, DB_ROOT_PASSWORD를 로컬용 값으로 변경
docker compose up -d
docker compose ps
```

두 서비스 모두 `127.0.0.1`에만 포트를 바인딩해 외부에 노출되지 않는다. ClamAV는 최초 실행 시 시그니처 데이터베이스를 적재하므로 healthy 상태가 되기까지 1~3분 걸릴 수 있다.

```bash
docker compose logs -f clamav   # 진행 상황 확인
docker compose exec clamav clamdscan --ping=1   # 연결 확인
docker compose exec mysql mysql -u file_guard -p file_guard   # 접속 확인
```

## 2. 백엔드

```bash
cd block_file_extensions
./mvnw spring-boot:run
```

프로젝트 루트의 `.env`를 자동으로 읽는다(`application.properties`의 `spring.config.import`). 기동 시 Flyway가 마이그레이션을 자동 적용한다.

## 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

`vite.config.js`의 프록시 설정으로 `/api/*` 요청이 `http://localhost:8080`(백엔드)으로 전달된다. `http://localhost:5173`에서 확인한다.

## 4. 종료

```bash
docker compose down       # 데이터 유지
docker compose down -v    # 볼륨까지 완전히 삭제 (필요할 때만)
```
