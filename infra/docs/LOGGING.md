# 통합 로그 조회 · E2E 재현 절차 (S15P11A503-104)

프론트·백엔드·AI·WebRTC 로그를 **한 화면의 같은 시간축**에서 보는 방법과,
대표 시나리오(시민 제보)를 재현하며 실패 지점을 짚는 절차다.

`docker compose logs`로 컨테이너를 하나씩 여는 방식은 MONITORING.md 3장에 그대로
남아 있다. 그건 단일 컨테이너를 깊게 볼 때 쓰고, 여러 컴포넌트에 걸친 문제는 이 문서를 쓴다.

---

## 1. 수집 구조

OpenVidu 스택이 이미 Loki + Grafana + Alloy를 운영 중이라 **새로 띄운 것은 없다.**

```
각 컨테이너 stdout/stderr
  → docker json-file (서비스당 30MB 상한, 유지됨)
  → Alloy (docker API로 읽음, refresh 5s)
  → Loki (보존 32일, 파일시스템)
  → Grafana
```

Alloy는 컨테이너 이름이 아니라 **docker 라벨 `logging=true`로 수집 대상을 고른다**
(`/opt/openvidu/config/config.alloy`의 `discovery.docker` 필터). 그래서 우리
`docker-compose.yml`에서 `labels: *loki-logging`을 붙인 서비스만 들어간다.

| 컨테이너 | 라벨 | 소유 |
|---|---|---|
| `infra-backend-1` | ✅ | 우리 |
| `infra-nginx-1` | ✅ | 우리 |
| `ai-server` | ✅ (배포 시 자동 편입) | 우리 |
| `infra-db-1` `infra-redis-1` `infra-minio-1` `infra-uptime-kuma-1` | ✗ | 우리 |
| `openvidu` `egress` `ingress` `caddy` `dashboard` `mongo` `redis` `minio` | ✅ | OpenVidu 스택 |

OpenVidu 쪽 파일은 건드리지 않는다 — 업그레이드 시 덮어써진다.

> **라벨을 새로 붙이면 그 컨테이너는 재생성된다.** `db`/`redis`/`minio`를 뺀 이유가
> 이것이다(현재 티켓 범위 밖 + 불필요한 재기동 회피). 필요해지면 그때 추가한다.

### 커스텀 라벨은 무의미하다

Alloy의 relabel 규칙은 `__meta_docker_container_name` 하나만 Loki 라벨(`container`)로
옮긴다. `stack=safy` 같은 라벨을 붙여도 Loki에서 조회에 쓸 수 없다.
**우리 서비스 구분은 `{container=~"infra-.*"}`로 한다.**

---

## 2. 접근

### 2.1 Grafana (팀 공용)

```
https://i15a503.p.ssafy.io/grafana        →  Explore  →  데이터소스 Loki
```
로그인 계정은 서버의 `/opt/openvidu/config/openvidu.env`에서
`GRAFANA_ADMIN_USERNAME` / `GRAFANA_ADMIN_PASSWORD`로 확인한다.
(OpenVidu 스택이 발급한 값이라 이 저장소에는 적지 않는다.)

### 2.2 Loki API (스크립트·빠른 확인용)

Loki는 `auth_enabled: false`라 **인증 없이** 조회된다. host 네트워크로 `0.0.0.0:3100`에
바인딩돼 있고, 외부 접근은 SSAFY 보안그룹이 3100을 막아서 차단된다
(2026-07-29 실측: 외부에서 접속 시 타임아웃).

> 즉 **막고 있는 것은 컨테이너 설정이 아니라 보안그룹 하나뿐이다.** 3100이 열리는 순간
> 전체 로그가 무인증으로 노출된다. OpenVidu 소유 설정이라 우리가 바꾸지 않지만,
> 포트 정책을 손댈 일이 생기면 이 항목을 먼저 확인할 것.

```bash
ssh -i <키파일> ubuntu@i15a503.p.ssafy.io

# 지금 수집 중인 컨테이너 목록
curl -sG "http://127.0.0.1:3100/loki/api/v1/label/container/values"

# 최근 15분 backend 로그
END=$(date +%s)000000000; START=$(( $(date +%s) - 900 ))000000000
curl -sG "http://127.0.0.1:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={container="infra-backend-1"}' \
  --data-urlencode "start=$START" --data-urlencode "end=$END" \
  --data-urlencode 'limit=50'
```

---

## 3. 자주 쓰는 쿼리

```logql
{container=~"infra-.*"}                                  # 우리 스택 전부
{container=~"infra-backend-1|infra-nginx-1|openvidu|egress|caddy"}   # 제보 전 구간
{container="infra-nginx-1"} |~ " (4|5)[0-9][0-9] "       # nginx 4xx/5xx만
{container="infra-backend-1"} |= "ERROR"                 # 백엔드 예외
{container=~"openvidu|egress"}                           # WebRTC·녹화
{container="openvidu"} | room_id != ""                   # 특정 방 추적(Alloy가 RM_* 추출)

sum by (container) (count_over_time({container=~"infra-.*"}[5m]))    # 컨테이너별 유입량
```

`level` 라벨은 Alloy가 본문에 `warn`/`error` 문자열이 있으면 붙인다.
정확한 레벨이 아니라 문자열 매칭이므로 필터 보조용으로만 쓴다.

---

## 4. 대표 시나리오 재현 절차 (시민 제보)

브라우저에서 `https://i15a503.p.ssafy.io/app` 로 제보 1건을 올리고, Grafana Explore에
아래 쿼리를 걸어 **같은 시간축에서** 단계별로 확인한다.

```logql
{container=~"infra-nginx-1|infra-backend-1|openvidu|egress"}
```

| # | 단계 | 어디서 보이나 | 확인 값 |
|---|---|---|---|
| 1 | 정적 자산·API 진입 | `infra-nginx-1` | `GET /app` 200, `POST /api/v1/...` |
| 2 | 인증 | `infra-nginx-1` | 401이면 쿠키 미전달 |
| 3 | 위치 → 자치구 변환 | `infra-backend-1` | `KakaoAdministrativeAreaResolver` 예외 없음 |
| 4 | presigned URL 발급 | `infra-backend-1` | 발급 실패 시 예외 |
| 5 | 브라우저 → MinIO 업로드 | `infra-nginx-1` (9200 서버 블록) | `PUT ...` 200. 403이면 6장 참고 |
| 6 | 제보 저장 | `infra-backend-1` | INSERT 롤백이면 6장 참고 |
| 7 | 라이브 제보 시 WebRTC | `openvidu` | 방 생성 `RM_*` |
| 8 | 녹화 | `egress` | Egress 트리거 |

빠른 왕복 확인은 마커를 심는 방식이 편하다.

```bash
M=CORRTEST-$(date +%s)
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:6080/api/v1/reports?probe=$M"
# 12초 뒤 Grafana Explore에서:  {container=~"infra-.*"} |= "CORRTEST-..."
```

### ⚠ 한계 — 요청 단위 상관관계는 안 된다

Spring Boot가 **요청별 access 로그를 남기지 않는다**(2026-07-29 실측: 위 마커
요청이 nginx에는 찍히고 backend에는 안 찍힘). 따라서

- 정상 요청의 nginx ↔ backend 연결은 **타임스탬프로만** 맞춘다.
- 예외가 나면 backend가 스택트레이스를 남기므로 실패 경로 추적은 문제없다.
- 요청 ID 기반 추적이 필요해지면 backend에 access 로그나 MDC trace ID를 켜야 한다.
  이건 백엔드 영역이라 별도 합의 사항.

브라우저 콘솔 로그는 Loki에 들어오지 않는다. **프론트 계층에서 볼 수 있는 것은
nginx access 로그가 전부다**(어떤 자산·API가 어떤 상태코드로 갔는지).

---

## 5. 배포 중 로그가 끊기는 구간

`deploy` 잡은 `docker compose up -d --build backend db redis minio nginx uptime-kuma`를
실행한다. `backend`와 (설정이 바뀐 경우) `nginx`가 재생성되며 **약 15초** 502가 난다
(backend 부팅 13.1초 실측). 이 구간의 502는 장애가 아니다.

재생성된 컨테이너는 ID가 바뀌지만 이름은 같아 Loki에서 연속으로 보인다.

---

## 6. 알려진 실패 지점

E2E 도중 실제로 겪은 것들이다. 배경은 `INFRA_DECISIONS.md` 참고.

| 증상 | 로그에서 보이는 모습 | 원인 |
|---|---|---|
| MinIO 업로드 403 | `infra-nginx-1`에 `PUT` 403 | presign 서명에 host+port가 포함된다. `MINIO_PUBLIC_ENDPOINT`가 브라우저 접속 주소와 다르면 실패 |
| 로그인 세션이 안 붙음 | `infra-nginx-1`에 401 반복 | `AUTH_COOKIE_SECURE=true`라 http(8091)에서는 쿠키가 안 실린다. https 주소를 쓸 것 |
| 도메인 루트가 `/meet`으로 302 | `caddy`에 502 | 6080 리스너가 죽음 → `infra-nginx-1` 상태 확인 |
| 제보가 관제센터에 미배정 | `infra-backend-1`에 `KakaoAdministrativeAreaResolver` 예외 반복 | `KAKAO_LOCAL_REST_API_KEY` 미설정 |
| INSERT 롤백 | `infra-backend-1`에 CHECK 제약 위반 | `ddl-auto: update`는 기존 CHECK 제약을 갱신하지 않는다. enum 값 추가 시 수동 반영 필요 |
| 배포했는데 옛 버전 | — | `DEPLOY_TAG` 없이 `docker compose up -d backend`를 치면 `latest`로 조용히 롤백된다 |

---

## 7. 아직 없는 것

- **자원 사용량 메트릭**(CPU/메모리/디스크). Prometheus는 `openvidu`(LiveKit) 하나만
  수집한다. 부하 측정(S15P11A503-106)에는 cAdvisor 추가 또는 `docker stats` 수집이
  필요하다. Prometheus 설정은 OpenVidu 소유라 별도 판단.
- **알림 연동.** 로그 기반 알림은 없다. 서비스 다운 알림은 Uptime Kuma → Mattermost
  (MONITORING.md 참고).
- **AI 서버 로그.** 라벨은 붙여뒀으나 배포 전이라 아직 컨테이너가 없다.
