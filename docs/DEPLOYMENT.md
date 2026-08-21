# DEPLOYMENT.md — Docker Hub + EC2 docker compose 배포

> GitHub Actions는 쓰지 않는다. 로컬(또는 어디서든)에서 이미지를 빌드해 Docker Hub에 push하고,
> EC2 서버에서는 `docker compose pull && docker compose up -d`로 그 이미지를 받아 실행한다.
> `.env`는 저장소에 올리지 않고 EC2 서버에 직접 작성한다.

---

## 1. 전체 구조

```
[로컬/CI 없음]                          [EC2 인스턴스]
  docker build (backend, frontend)        docker compose (mysql, clamav, backend, frontend)
  docker push → Docker Hub          →      ↑ docker compose pull로 이미지만 받아옴
```

- **frontend 컨테이너(Nginx)**: 정적 파일을 서빙하면서 `/api/*` 요청을 내부 네트워크의 `backend` 컨테이너로 프록시한다. 브라우저 입장에서는 프론트와 API가 같은 오리진이라 CORS를 신경 쓸 필요가 없다.
- **backend, mysql, clamav**: 호스트에 포트를 열지 않는다. Docker 내부 네트워크(`fileguard-network`)에서만 서로 통신한다. 외부에 노출되는 건 `frontend`의 80번 포트 하나뿐이다.
- **업로드 파일**: `uploads_data`라는 이름의 Docker volume에 저장돼 컨테이너를 재생성해도 유지된다.

관련 파일:
| 파일 | 역할 |
|---|---|
| `block_file_extensions/Dockerfile` | 백엔드 이미지 (Maven 빌드 → JRE 실행) |
| `frontend/Dockerfile` | 프론트 이미지 (Vite 빌드 → Nginx 서빙) |
| `frontend/nginx.conf` | 정적 파일 서빙 + `/api` 프록시 설정 |
| `docker-compose.prod.yml` | EC2에서 실행할 compose 정의 (mysql/clamav/backend/frontend) |
| `.env.production.example` | EC2에 직접 작성할 `.env`의 참고 템플릿 (실제 비밀값은 담지 않음) |

---

## 2. 이미지 빌드 & Docker Hub push (로컬에서)

### 2-1. 로그인

```bash
docker login
```

### 2-2. 아키텍처 주의 — Apple Silicon Mac에서 빌드하는 경우

로컬(맥)이 ARM64이고 EC2가 보통 쓰는 x86_64(t2/t3 계열) 인스턴스라면, 아무 옵션 없이 `docker build`만 하면 EC2에서 `exec format error`로 컨테이너가 뜨지 않는다. **`buildx`로 타깃 아키텍처를 명시해서 빌드 + push를 한 번에 한다.**

```bash
docker buildx create --use --name fileguard-builder 2>/dev/null || docker buildx use fileguard-builder
```

(EC2를 ARM 인스턴스(t4g 계열)로 쓸 거라면 `--platform linux/arm64`로 바꾸면 된다.)

### 2-3. 백엔드 이미지

```bash
cd block_file_extensions
docker buildx build --platform linux/amd64 \
  -t kimchano/block-file-extensions-backend:latest \
  --push .
cd ..
```

### 2-4. 프론트엔드 이미지

```bash
cd frontend
docker buildx build --platform linux/amd64 \
  -t kimchano/block-file-extensions-frontend:latest \
  --push .
cd ..
```

### 2-5. (권장) 버전 태그도 함께 push

`latest`만 쓰면 배포 후 문제가 생겼을 때 "직전 버전"이 뭐였는지 알 수 없다. 커밋 해시나 날짜로 태그를 하나 더 붙여두면 롤백이 쉬워진다.

```bash
TAG=$(git rev-parse --short HEAD)
docker buildx build --platform linux/amd64 \
  -t kimchano/block-file-extensions-backend:latest \
  -t kimchano/block-file-extensions-backend:$TAG \
  --push block_file_extensions

docker buildx build --platform linux/amd64 \
  -t kimchano/block-file-extensions-frontend:latest \
  -t kimchano/block-file-extensions-frontend:$TAG \
  --push frontend
```

---

## 3. EC2 최초 세팅 (한 번만)

### 3-1. Docker 설치

아래 명령은 **EC2 Amazon Linux 2023 AMI** 기준이다. Amazon Linux 2023의 `yum`은 내부적으로 `dnf`를 사용하므로 `dnf`를 직접 입력하지 않아도 된다. 먼저 AMI를 확인한다.

```bash
cat /etc/os-release
```

```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

그룹 권한은 SSH에서 로그아웃한 뒤 다시 접속해야 적용된다. 재접속 후 다음 명령으로 확인한다.

```bash
docker info
docker compose version
```

Amazon Linux의 `docker` 패키지에 Compose 플러그인이 포함되지 않아 `docker: 'compose' is not a docker command`가 나오면 사용자 플러그인 경로에 직접 설치한다.

```bash
COMPOSE_VERSION=v5.4.0
COMPOSE_ARCH=$(uname -m)

mkdir -p "$HOME/.docker/cli-plugins"
curl -fSL \
  "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${COMPOSE_ARCH}" \
  -o "$HOME/.docker/cli-plugins/docker-compose"
chmod +x "$HOME/.docker/cli-plugins/docker-compose"

docker compose version
```

> Amazon Linux 2 AMI를 사용한다면 Docker 설치 명령만 `sudo amazon-linux-extras install docker -y`로 바꾼다. 이후 서비스 시작과 그룹 설정은 동일하다.

### 3-2. 배포 디렉토리 준비

```bash
sudo mkdir -p /opt/fileguard
sudo chown $USER:$USER /opt/fileguard
cd /opt/fileguard
```

이 저장소에서 **딱 두 파일만** EC2로 가져오면 된다 (소스 전체를 클론할 필요 없음 — 실행에 필요한 건 이미지와 compose 정의뿐):
- `docker-compose.prod.yml`
- `.env.production.example` (참고용, 실제로는 이 내용을 기반으로 `.env`를 직접 작성)

```bash
# 로컬에서 EC2로 scp (예시)
scp docker-compose.prod.yml ec2-user@<EC2_HOST>:/opt/fileguard/
```

### 3-3. `.env` 직접 작성

`/opt/fileguard/.env`를 `.env.production.example`을 참고해 EC2에서 직접 만든다 (에디터로 직접 입력하거나 `scp`로 옮긴 뒤 값만 채워도 됨). **`DB_PASSWORD`/`DB_ROOT_PASSWORD`는 반드시 강력한 값으로 바꾼다.**

```bash
vi /opt/fileguard/.env
chmod 600 /opt/fileguard/.env
```

### 3-4. 리소스 여유 확인

Spring, MySQL, ClamAV를 한 인스턴스에서 실행하면 ClamAV의 시그니처 적재 중 메모리 사용량이 크게 늘어난다. 안정적인 운영은 t3.medium(4GB) 이상을 권장한다. t3.small(2GB)을 사용한다면 최소 2GB Swap을 추가하고, t2/t3.micro(1GB)는 사용하지 않는다.

```bash
free -h
swapon --show
df -h /
```

t3.small에서 Swap이 없다면 다음 명령을 한 번만 실행한다.

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab

free -h
swapon --show
```

---

## 4. 최초 배포 / 이후 배포 (매번 반복)

```bash
cd /opt/fileguard
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

- `pull`이 Docker Hub에서 `.env`의 `IMAGE_TAG`(기본 `latest`)에 해당하는 최신 이미지를 받아온다.
- `up -d`가 바뀐 이미지가 있는 서비스만 재생성한다. mysql/clamav는 이미지가 그대로면 재시작되지 않는다(데이터 볼륨 유지).
- 최초 실행 시 Flyway가 자동으로 마이그레이션을 적용하고, ClamAV는 시그니처 DB를 받느라 healthy 상태가 되기까지 1~3분 걸릴 수 있다(`docker compose logs -f clamav`로 확인).

### 특정 버전으로 롤백

```bash
# .env에서 IMAGE_TAG를 이전 커밋 해시로 바꾼 뒤
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

---

## 5. 배포 후 확인

```bash
curl -I http://127.0.0.1/                          # 프론트 정적 파일
curl http://127.0.0.1/api/policy/fixed-extensions  # 백엔드 프록시 경유 확인
```

두 요청이 정상 응답한 다음 ngrok을 연결한다.

---

## 6. EC2에 ngrok 설치 및 HTTPS 주소 생성

### 6-1. ngrok 설치 — Amazon Linux 2023 EC2

Amazon Linux는 대시보드의 Debian/Ubuntu용 `apt` 명령을 사용할 수 없다. Linux `Download`에 제공되는 x86-64 바이너리를 직접 설치한다. t3 계열은 x86-64이며, t4g 같은 ARM 인스턴스라면 파일명의 `amd64`를 `arm64`로 바꾼다.

```bash
curl -fSL \
  https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-amd64.tgz \
  -o /tmp/ngrok.tgz
sudo tar -xzf /tmp/ngrok.tgz -C /usr/local/bin
sudo chmod 755 /usr/local/bin/ngrok

ngrok version
```

### 6-2. 계정 토큰 등록

ngrok 대시보드에서 발급받은 Authtoken을 등록한다. 실제 토큰은 저장소나 배포 문서에 기록하지 않는다.

```bash
ngrok config add-authtoken "<NGROK_AUTHTOKEN>"
ngrok config check
```

### 6-3. 포그라운드에서 먼저 확인

이 프로젝트는 Spring Boot의 8080 포트를 호스트에 공개하지 않고, frontend Nginx만 호스트의 80번 포트에 공개한다. 따라서 터널 대상은 반드시 `80`이다.

```bash
ngrok http 80
```

출력된 `https://...ngrok-free.app` 주소에서 React 화면과 `/api/*` 요청이 모두 동작하는지 확인한 뒤 `Ctrl+C`로 종료한다.

### 6-4. 재부팅 후에도 실행되도록 서비스 등록

`nohup`보다 ngrok의 systemd 서비스 기능을 사용한다. 설정 파일을 편집해 기존 `agent.authtoken`은 유지하고 `endpoints`를 추가한다.

```bash
ngrok config edit
```

```yaml
version: 3

agent:
  authtoken: <기존에 등록된 토큰>

endpoints:
  - name: fileguard
    upstream:
      url: 80
```

`ngrok config check`에 출력된 실제 설정 파일 경로를 사용한다. 기본 `ec2-user`라면 일반적으로 다음 경로다.

```bash
sudo ngrok service install --config /home/ec2-user/.config/ngrok/ngrok.yml
sudo ngrok service start
```

상태와 공개 URL을 확인한다.

```bash
sudo systemctl status ngrok --no-pager
curl http://127.0.0.1:4040/api/endpoints
```

**보안 그룹**: ngrok만 외부 진입점으로 사용한다면 인바운드 80과 8080은 열지 않아도 된다. SSH 22만 본인 IP로 제한한다. MySQL 3306과 ClamAV 3310도 외부에 열지 않는다. ngrok 에이전트가 EC2에서 외부로 연결하므로 일반적인 아웃바운드 HTTPS 연결은 허용되어 있어야 한다.

---

## 7. 미해결/향후 과제

- **무중단 배포 아님**: `up -d`로 컨테이너를 교체하는 동안 짧은 다운타임이 있다. 트래픽이 실질적으로 없는 평가/데모 목적이라 blue-green이나 롤링 배포는 이번 범위에서 다루지 않는다.
- **ngrok 의존**: 제출 URL의 HTTPS는 ngrok이 종료한다. 향후 자체 도메인으로 운영한다면 Nginx 443 + 인증서 구성을 별도로 적용한다.
- **DB 백업**: `mysql_data` 볼륨은 인스턴스가 살아있는 한 유지되지만, 정기 백업(`docker exec fileguard-mysql mysqldump ...`)은 별도로 구성해야 한다.
