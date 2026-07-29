# 서비스 헬스체크 · 로그 · 장애 알림 (S15P11A503-48)

우리 `docker-compose.yml` 스택(backend / db / redis / minio / nginx)의 **가동 상태를
한 곳에서 확인**하고, **장애 시 담당자가 Mattermost 알림**을 받도록 하는 구성 문서다.

- OpenVidu는 별도 공식 스택으로 자체 관측 도구(grafana/prometheus/loki)를 이미 갖고 있어
  이 문서의 대상이 아니다. 필요 시 Kuma에 도메인 외형 감시만 추가한다(아래 6번).
- AI 서버는 기능 미완성으로 현재 배포 대상이 아니다(프론트·백만 배포). Kuma에 모니터를
  등록하되 **일시정지** 상태로 두고, AI 배포 시작 시 활성화한다.

---

## 1. 구성 요약

| 구성요소 | 위치 | 역할 |
|---|---|---|
| 컨테이너 헬스체크 | `docker-compose.yml`의 각 서비스 `healthcheck:` | 컨테이너 스스로 정상 여부 보고(`docker compose ps`) |
| nginx `/healthz` | `nginx/default.conf` | 항상 200을 주는 헬스 전용 엔드포인트 |
| Uptime Kuma | `docker-compose.yml`의 `uptime-kuma` 서비스 | 전 서비스 상태 대시보드 + 장애 알림 |
| 로그 로테이션 | `docker-compose.yml`의 `x-logging` 앵커 | 서비스당 로그 30MB 상한(디스크 보호) |

---

## 2. 서비스별 헬스체크 방식

| 서비스 | 방식 | 세부 |
|---|---|---|
| `db` (PostgreSQL) | 컨테이너 헬스체크 | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` |
| `redis` | 컨테이너 헬스체크 | `redis-cli ping` → `PONG` |
| `nginx` | 컨테이너 헬스체크 | `wget --spider http://localhost/healthz` (항상 200) |
| `backend` (Spring Boot) | Kuma TCP 모니터 | jre-alpine에 curl 없음 + 무인증 200 엔드포인트 없음 → 컨테이너 헬스체크 대신 Kuma가 `backend:8080` TCP 감시 |
| `minio` | Kuma HTTP 모니터 | 최신 minio 이미지에 curl/mc 없음 → Kuma가 `minio:9000/minio/health/live` 감시 |
| `ai-server` (FastAPI) | Kuma HTTP 모니터(일시정지) | `/health` 존재하나 현재 미배포 → 배포 시 활성화 |

`docker compose ps`로 db/redis/nginx의 `(healthy)` 여부를 즉시 확인할 수 있다.

---

## 3. 로그 확인 방식

여러 컴포넌트에 걸친 문제(제보 전 구간, WebRTC 등)는 Grafana에서 통합 조회하는 편이
빠르다. 수집 구조·쿼리·E2E 재현 절차는 **`LOGGING.md`** 참고. 아래는 단일 컨테이너를
깊게 볼 때 쓰는 방식이다.

### 3.1 서버 접속
```bash
ssh -i <키파일> ubuntu@i15a503.p.ssafy.io
cd ~/S15P11A503/infra
```

### 3.2 컨테이너 로그 조회
```bash
docker compose ps                 # 전체 서비스 상태 + (healthy) 표시
docker compose logs -f backend    # backend 실시간 로그
docker compose logs --tail=200 nginx
docker compose logs --since=30m   # 최근 30분 전체
```

### 3.3 로그 로테이션
`x-logging` 앵커로 모든 서비스에 json-file 드라이버 상한을 걸어둔다.

- 서비스당 **최대 10MB × 3파일 = 30MB**. 초과분은 자동으로 오래된 것부터 삭제된다.
- 로그 원본 위치(서버): `/var/lib/docker/containers/<컨테이너ID>/*-json.log`
- 반영 확인: `docker inspect <컨테이너> --format '{{json .HostConfig.LogConfig}}'`

---

## 4. Uptime Kuma 접근

대시보드는 보안을 위해 **공개하지 않고 `127.0.0.1:3001`에만 바인딩**한다.
로컬 PC에서 SSH 터널로 접근한다.

```bash
# 로컬 PC에서 실행 → localhost:3001 이 서버의 Kuma로 연결됨
ssh -i <키파일> -L 3001:localhost:3001 ubuntu@i15a503.p.ssafy.io
```
터널을 연 상태로 브라우저에서 `http://localhost:3001` 접속.

- 최초 접속 시 관리자 계정을 생성한다(계정 정보는 팀 비밀 채널에서 공유, 저장소에 커밋 금지).
- 외부 공개가 꼭 필요하면 ufw + Lightsail 보안그룹 개방이 별도로 필요하다. 기본은 비공개 유지.

---

## 5. Kuma 모니터 구성 (웹UI에서 등록)

Kuma 설정은 볼륨(`uptime_kuma_data`)에 저장되며 git에는 담기지 않는다.
아래 목록대로 UI에서 등록한다. 모든 대상은 Kuma와 같은 compose 네트워크에 있으므로
**서비스 이름**으로 접근한다.

| 이름 | 모니터 종류 | 대상 |
|---|---|---|
| backend | TCP Port | `backend` : `8080` |
| nginx | HTTP(s) | `http://nginx:80/healthz` |
| minio | HTTP(s) | `http://minio:9000/minio/health/live` |
| db (PostgreSQL) | TCP Port | `db` : `5432` |
| redis | TCP Port | `redis` : `6379` |
| ai-server *(일시정지)* | HTTP(s) | `http://ai-server:8000/health` — AI 배포 시 활성화 |

### 5.1 안티노이즈 설정 (모든 모니터 공통)
배포(`docker compose up -d`) 시 컨테이너가 잠깐 재시작되며 깜빡이는 것을 알림으로
오인하지 않도록 아래 값으로 둔다.

- **Heartbeat Interval**: 60초
- **Retries**: 3 (연속 3회 실패해야 DOWN 확정 → 3분 이상 죽어야 알림)
- **Heartbeat Retry Interval**: 60초
- **Resend Notification**: 끔(0) — 같은 장애로 반복 도배하지 않음

> 근거: 알림은 상태 전이(UP→DOWN/DOWN→UP) 시에만 발생하며 체크 횟수와 무관하다.
> 정상 운영 중에는 알림이 발생하지 않고, 배포 재시작(보통 1분 이내)은 위 임계값을
> 넘지 않아 걸러진다. 실제 3분 이상 장애일 때만 1회 알림된다.

---

## 6. Mattermost 알림 연결

1. Mattermost에서 알림 받을 채널의 **Incoming Webhook**을 생성해 URL을 확보한다.
   (웹훅 URL은 비밀값 → 저장소에 커밋 금지. Kuma UI에만 입력하며 볼륨에 저장된다.)
2. Kuma → Settings → Notifications → **Setup Notification** → Notification Type: **Mattermost**.
3. Webhook URL 입력, (선택) Username/Channel 지정 후 **Test**로 채널에 테스트 메시지 확인.
4. Save 후, 각 모니터의 편집 화면에서 이 알림을 **활성화**한다.

---

## 7. 장애 대응 흐름

1. Mattermost에 `[DOWN] <서비스>` 알림 수신.
2. SSH 접속 → `docker compose ps`로 어떤 서비스가 죽었는지 확인.
3. `docker compose logs --tail=200 <서비스>`로 원인 로그 확인.
4. 조치: `docker compose up -d <서비스>` 재기동, 또는 backend 문제면 `scripts/rollback.sh`로 롤백.
5. 복구되면 Kuma가 자동으로 UP 감지 → `[UP]` 알림 수신으로 확인.
