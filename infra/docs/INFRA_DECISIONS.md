# 2026-07-20 (월) — OpenVidu local-deployment 실습 & 포트 조사

**오늘의 원칙**: 공유 서버(Lightsail) 미접촉, 로컬에서 먼저 트러블슈팅

## 1. 로컬 환경 세팅
- Docker Desktop 신규 설치 (Windows/AMD64), WSL2 백엔드
- Wi-Fi IP 확인 결과 사설 대역 아닌 공인 IP(SSAFY 캠퍼스 네트워크 특성) — LAN IP 스크립트 정상 매칭 확인됨
- 포트 충돌(80/443) 없음 확인

## 2. OpenVidu Community v3.8.0 local-deployment 기동
- openvidu-local-deployment/community 클론 → docker compose up 성공 (전체 11개 컨테이너 Up)
- Dashboard(localhost:7880/dashboard), Meet(localhost:9080) 접속 확인
- Room 생성 → Participant 등록 → Dashboard Active Rooms/Participants 실시간 반영 확인 (SAFY 관리자 모니터링 구조의 실동작 근거 확보)
- 팀원 LAN 내 PC 접속 성공

## 3. 미해결 — 모바일 접속 실패
- 폰(본인) → LAN 도메인/IP 둘 다 무한 대기로 실패
- 원인 추정: 로컬 self-signed 인증서 + .dev 커스텀 도메인(HSTS preload 대상) 조합을 모바일 브라우저가 데스크톱과 다르게 처리하는 것으로 추정. 실제 LiveKit/OpenVidu 자체는 모바일 WebRTC 지원함
- 후속 조치: 서버에 정식 도메인 + Let's Encrypt 인증서 적용 후 재검증 필요. 로컬 환경 한계로 결론 보류
- Windows 방화벽 Public 프로파일 가능성도 배제 안 됨 (미확인)

## 4. UDP 포트 공식 문서 조사
- 출처: openvidu.io/latest/docs/self-hosting/single-node/on-premises/install/ (Community, v3.8, 7/20 확인)
- 핵심 포트: TCP 80/443, UDP 443(STUN/TURN, 오전 초안 누락분), TCP 1935(선택), TCP 7881, UDP 7885, TCP 9000(선택), UDP 50000-60000(메인 미디어)
- local-deployment 실측 포트와 일부 차이 확인(예: UDP 7885 vs 7895) — 서버 적용은 공식 문서 기준
- SSAFY 예약 포트(8988/8989/29418)와 충돌 없음 확인
- WEBRTC_INFRA_ROLE_GUIDE.md 3.8절 갱신 완료 (상태: 1차 조사, 서버 적용 전)

## 5. infra 폴더 초기 구성
- infra/docker-compose.yml, infra/env.example 작성
- MinIO 포트 9000 — OpenVidu 자체 MinIO(Egress, public 노출 시)와 겹칠 가능성 발견, 주석으로 미해결 표시
- infra 하위 nginx/, openvidu/, scripts/, docs/ 폴더 구조 생성
- feature/geonwoo-infra-docker 브랜치에 커밋 및 push 완료
- develop 병합은 보류 — 백엔드 인원 ERD/구조 설계 단계로 이니셜 커밋 전이라 아직 급하지 않음. 백엔드 이니셜 커밋 시점에 맞춰 병합 예정

## 6. 오늘 배운 점
- local-deployment는 로컬 전용 경량 구성이라 실서버 포트 구성과 다름 — 그대로 서버에 적용하면 안 됨
- SSAFY Wi-Fi가 사설 IP가 아닌 공인 IP를 직접 배포하는 구조라, 향후 네트워크 관련 작업 시 계속 염두에 둘 것

## 내일(7/21) 방향 메모
- 모바일 접속 이슈: Windows 방화벽 프로파일 확인 또는 서버 배포 후 재검증 중 택일
- 서버 배포 기반 구성 착수 여부 결정
- Redis 용도, 녹화 블러 처리, AI 서버 fps — 팀 미결 항목 계속 확인 필요

## 2026-07-20 (월) 오후 — LiveKit CLI 토큰 발급 검증

**목표**: 로컬 OpenVidu 기동 확인 + 토큰 발급 테스트

**진행**
- LiveKit CLI(`lk` v2.18.0) Windows 설치 (winget)
- `lk token create`로 devkey/secret 기반 JWT 토큰 발급 성공
  - 최초 시도 시 PowerShell 백틱(`) 줄바꿈이 깨지면서 `--url` 플래그 인식 실패 → LiveKit Cloud 인증 페이지로 잘못 유도되는 이슈 발생, 한 줄 명령으로 재시도해 해결
- `lk room join`으로 CLI가 실제 test-room에 참가자로 입장 성공 (CONNECTED 상태 로그 확인)
- OpenVidu Dashboard에서 Active Rooms: 1, Active Participants: 1로 실시간 반영 확인

**의미**
- SAFY 세션 트리거 흐름(3.3절)의 "Spring Boot → OpenVidu 토큰 발급" 단계가 실제로 API Key/Secret 서명 방식으로 동작함을 로컬에서 직접 검증
- 추후 Spring Boot 연동 시 이 흐름을 서버 SDK로 그대로 옮기면 됨

**부가 확인 — Dashboard Analytics**
- Bandwidth 지표에서 Upstream(370MB) 대비 Downstream(571MB)이 더 큰 것 확인
- SFU 구조 특성상 참가자 수가 늘수록 Downstream이 배로 증가하는 것을 실측으로 확인 — 향후 서버 사양 산정 시 참고 자료

**상태**: 오전 목표(로컬 기동 확인 + 토큰 발급 테스트) 완료

---

# 2026-07-21 (화) — 실서버 최초 접촉: Linux 기본설정 + OpenVidu 실배포 + 검증

**오늘의 원칙**: 오전은 Lightsail 실서버 최초 접속 및 Linux/ufw 기본 세팅, 오후는 Docker+OpenVidu 실배포 후 포트·Egress·UDP 실측 검증까지 완료

## 1. 오전 — Linux 기본 설정 + ufw 방화벽
- SSH 접속 확인 (`ubuntu@i15a503.p.ssafy.io`), `.pem` 키 파일(`I15A503T.pem`) 권한 정리(`icacls`)
- `apt update && apt upgrade -y` 완료, 재부팅 필요 여부 확인 후 작업 종료 시점으로 재부팅 이연
- `ufw` 기존 상태 확인 — SSAFY 사전 세팅(22, 8989/tcp, 443) 존재 확인 후, 나머지 4개 포트(80/tcp, 7881/tcp, 7885/udp, 50000:60000/udp) 추가
  - 443은 프로토콜 미지정 등록이라 tcp+udp 동시 허용 상태였음을 확인, 추가 조치 불필요
- 재부팅 후 ufw 규칙 14개(v4 7 + v6 7) 유지 확인

## 2. 오후 — Docker 설치 + OpenVidu Community v3.8.0 실서버 배포
- Docker: 공식 설치 스크립트(`get-docker.sh`)로 최신 버전(29.6.2) 설치, Compose(v5.3.1) 포함 확인, 비root 사용자 권한(`usermod -aG docker`) 적용
- OpenVidu: 공식 온프레미스 설치 스크립트 사용, 도메인(`i15a503.p.ssafy.io`) + Let's Encrypt 인증서 자동 발급
- 컨테이너 14개(openvidu, caddy, minio, redis, mongo, egress, ingress, dashboard, openvidu-meet, grafana, alloy, prometheus, loki, operator) 정상 기동 확인
- `systemctl start openvidu`로 서비스 시작, 재부팅 없이 즉시 반영

## 3. 오후 — 다중 사용자 접속 및 UDP 경로 실측 검증
- 팀원 여러 명(모바일 포함) 동시 접속 성공, Participants 목록으로 확인
- `chrome://webrtc-internals` 덤프(JSON) 직접 분석 — selected candidate pair 확인 결과, 원격측이 `host` 타입 + UDP 프로토콜로 확인됨
- → TURN(relay) 우회 없이 UDP 50000-60000 대역을 직접 사용하고 있음을 데이터로 확정. 오전에 개방한 포트 구성이 이상적으로 작동 중임을 실증

## 4. 오후 — Egress 녹화 검증
- OpenVidu Meet UI에서 녹화 2건 실행 (3초, 9초), 상태 `COMPLETE`
- MinIO(OpenVidu 내장, Egress 전용) 저장 확인 및 실제 재생까지 검증 완료
- BE 연동 없이도 Egress 파이프라인 자체는 독립적으로 정상 동작함을 확인 — 2주차 예정 항목을 1주차에 조기 검증

## 5. 오후 — 시민제보용 MinIO 별도 컨테이너 기동
- OpenVidu 내장 MinIO(Egress 전용, 포트 9000/9001→내부 9100/9101)와 완전히 분리된 별도 컨테이너로 구성
- 최초 포트(9100/9101) 지정 시 OpenVidu 내장 MinIO와 충돌 발생 → `ss -tulnp` 전수 조사 후 9200/9201로 변경, 정상 기동
- 헬스체크(`curl -I http://localhost:9200/minio/health/live`) 200 OK 확인
- 현재는 외부 미공개(ufw 미개방) 상태, BE 연동 시점에 필요하면 포트 개방 예정

## 6. 오후 — 메모리 베이스라인 기록
- 시스템 전체: 15GB 중 사용 2.0GB, 여유 1.2GB, 캐시 12GB, Swap 0B
- 컨테이너 15개(신규 MinIO 포함) 합산 메모리 약 700MB, CPU 대부분 유휴 상태
- 2주차 부하 테스트(동시 라이브 3~5개 목표) 시 비교 기준값으로 사용 예정

## 7. 팀장 요청 처리 — 외부 API 아웃바운드 확인
- 서울시 실시간 도시데이터 API(`openapi.seoul.go.kr:8088`) 아웃바운드 통신 확인 요청 접수
- 서버에서 직접 curl 테스트, 실제 JSON 데이터 정상 수신 확인 → SSAFY 내부망만 막혀있던 것이고 배포 서버는 문제 없음을 회신

## 8. 기술 트러블슈팅 로그

| 시각 | 증상 | 원인 | 해결 | 배운 점 |
|---|---|---|---|---|
| 오전 | PowerShell에서 `Get-ChildItem` 실행 안 됨 | 실제로는 cmd(명령 프롬프트)를 PowerShell로 착각 | PowerShell 새로 실행해서 프롬프트(`PS C:\...>`) 확인 후 진행 | 터미널 종류는 프롬프트 표기로 먼저 확인 |
| 오전 | `sudo sh <(curl ...)` 실행 시 `cannot open /dev/fd/63` | `sudo`와 프로세스 치환(`<(...)`) 조합에서 파일 디스크립터 접근 실패 | `sudo -i`로 root 셸 전환 후, root 셸 안에서 sudo 없이 직접 실행 | sudo와 프로세스 치환은 같이 안 쓰는 게 안전 |
| 오전 | Docker 설치 스크립트 `Please run as root` | `ubuntu` 계정은 sudo 권한은 있지만 스크립트가 root 직접 실행 요구 | `sudo -i` → root 진입 후 재실행 | 위와 동일 맥락 |
| 오후 | 오전에 연 포트(80/443/7881)에 외부 `Test-NetConnection` 시 전부 `TimedOut` | 실제로는 해당 포트에서 대기 중인 서비스 자체가 없어서 발생한 정상적 현상 (SG 차단 아님) | 80번 포트에 임시 파이썬 서버(`sudo python3 -m http.server 80`)를 띄워 재검증 → 즉시 `TcpTestSucceeded: True` 확인 | 애매한 네트워크 에러는 "서비스 부재" 같은 단순 가설부터 실측으로 배제할 것 |
| 오후 | MinIO 신규 컨테이너 `docker compose up -d` 시 `address already in use (9100)` | 포트 사전 조사 없이 9100/9101 임의 지정 → OpenVidu 내장 MinIO가 이미 사용 중 | `ss -tulnp`로 사용 중인 포트 전수 확인 후 9200/9201로 변경 | 새 컨테이너 포트 지정 전 반드시 `ss -tulnp`로 기존 점유 여부 선확인 |

## 9. 오늘 자로 해소된 미결 사항 (WEBRTC_INFRA_ROLE_GUIDE.md 3.9절 대응)

| 원 가이드 미결 항목 | 오늘 상태 |
|---|---|
| OpenVidu 정확한 포트 구성 | 해소 — 공식 문서 기준 확정, 실측 검증 완료 |
| 녹화(Egress) 동작 여부 | 해소 — MinIO 저장, 재생까지 실측 확인 |
| UDP 미디어 릴레이 실제 도달 여부 | 해소 — webrtc-internals로 직접 UDP 경로 확인 |
| 녹화본 블러 처리 여부, 보관 기간 | 미해결 유지 — 팀 논의 필요 |
| Python(AI 서버) 실행 환경 | 미해결 유지 — AI 담당자 확인 필요 |
| `/ws/signaling` 엔드포인트 목적 | 미해결 유지 — BE 확인 필요 |

## 오늘 배운 점
- Lightsail(EC2)은 콘솔 접근 없이 SSH만 제공되는 구조라, 클라우드 콘솔로 눈으로 확인 가능한 것도 전부 서버 안에서 직접 찔러봐야 확인 가능
- 포트가 ufw에서 열려 있어도 그 위에서 대기 중인 서비스가 없으면 외부 검증 도구는 동일하게 "실패"로 보이므로, 방화벽 문제와 서비스 부재를 구분해서 검증해야 함
- webrtc-internals 덤프(JSON)를 직접 파싱하면 UI에서 눈으로 찾는 것보다 훨씬 정확하고 빠르게 candidate pair 타입을 확인 가능

## 내일(7/22) 방향 메모
- 원 계획(1주차 나머지: 팀원 크로스 네트워크 검증, BE/AI 미팅 조율) 중 상당 부분이 이미 오늘 조기 완료됨
- 2주차 항목 중 BE 의존 없는 것(Nginx 골격, 메모리 모니터링 루틴화) 조기 착수 검토
- 2주차 항목 중 "동시 다중 연결 부하 테스트"를 앞당겨 3주차 리스크 사전 검증 권장
- Redis 용도, 녹화 블러 처리, AI 서버 fps, Python 실행환경 — 팀 미결 항목 계속 확인 필요

---

# 2026-07-22 (수) — CI 파이프라인 초안 구축 + 실패 케이스 검증

**오늘의 원칙**: BE 의존 없는 2주차 인프라 항목을 조기 착수 — 서버를 건드리기 전에 CI(빌드/테스트 자동화) 골격부터 세운다.

## 1. GitLab CI 빌드/테스트 파이프라인 초안 (`2aa60dd`)
- `.gitlab-ci.yml` 신규 작성 — `test` 스테이지에 FE/BE/AI job 구성
  - `frontend-user`/`frontend-admin`: `node:22`, `npm ci → lint → build`, `rules: changes:`로 해당 서브트리 변경 시에만 실행
  - `backend`: `eclipse-temurin:21-jdk`, `./gradlew test build --no-daemon`
  - `ai`: `python:3.12-slim`, `pytest`
- **AI job은 CPU torch 휠(`ai/requirements-ci.txt`)로 분리** — CI 러너가 GPU가 아닐 가능성에 대비. prod용 `requirements.txt`(CUDA 휠)와 분리 유지.

## 2. CI 실패 케이스 의도적 검증 (`eec628c` → revert `32a3fbd`)
- 파이프라인이 "정상일 때 통과"만 보면 안 되고 "깨질 때 실제로 빨간불이 뜨는지"를 확인해야 신뢰 가능
- `frontend/src/api/client.ts`에 lint 에러를 **의도적으로 추가**해 CI가 실패로 잡는지 확인 → 확인 후 즉시 revert
- develop 병합(`c5331db`)으로 초안 확정

## 3. 오후 — 프론트 분리에 따른 CI job 분리 (`9fb7bde`)
- 프론트가 user/admin 두 프로젝트로 나뉨에 따라, 단일 frontend job을 `frontend-user`/`frontend-admin` 두 job으로 분리
- 각 job이 자기 서브트리 변경 시에만 도는 구조 유지(불필요한 빌드 방지)

## 4. 오늘 배운 점
- CI는 "성공 케이스"만 보면 반쪽짜리 — 실패를 일부러 만들어 빨간불을 확인해야 파이프라인을 신뢰할 수 있다
- GPU 유무가 불확실한 러너 환경에서는 무거운 의존성(torch)을 CI 전용 경량 휠로 분리해두는 게 안전

## 5. 내일(7/23) 방향 메모
- 백엔드 이니셜 커밋이 올라오면 Dockerfile + compose 실제 값 채우고 배포 스테이지 착수

---

# 2026-07-23 (목) — 백엔드 컨테이너화 + 배포 스테이지 + 실전 배포 트러블슈팅

**오늘의 원칙**: 빌드 자동화(CI)에서 배포 자동화(CD)로 확장 — 실제 서버에 붙이면서 나오는 문제는 로컬 재현으로 원인을 좁힌 뒤 고친다.

## 1. 오전 — 백엔드 컨테이너화 & compose 실측 교정
- **백엔드 Dockerfile (`f110d54`)**: 멀티스테이지 — build 스테이지(`eclipse-temurin:21-jdk`, `./gradlew bootJar -x test`) → run 스테이지(`21-jre-alpine`, 비root `spring` 유저, `java -jar app.jar`)
- **compose 경로/포트 교정 + nginx 스켈레톤 (`76e57e5`)**:
  - `build: ./backend`, `./ai` → **`../backend`, `../ai`로 수정** (compose는 파일 자기 위치 기준으로 상대경로를 풀기 때문에 `infra/backend`를 가리키던 오류)
  - MinIO 호스트 포트를 실서버 실측값 **9200/9201로 동기화** (7/21 OpenVidu 내장 MinIO 충돌로 재배정한 값)
  - 백엔드 호스트 매핑 8080 제거 → **8090(디버깅용)으로 변경** (8080은 OpenVidu ingress가 점유, `ss -tulnp` 확인). 컨테이너 내부는 8080 유지, 정식 진입점은 nginx(8091)
  - nginx 서비스 추가: frontend-user/admin dist 정적 서빙 + `/api/` 백엔드 프록시, 호스트 8091 노출(80/443은 Caddy 점유)
  - 구식 top-level `version` 키 제거

## 2. 낮 — 배포(CD) 스테이지 + SHA 태깅 + 롤백
- **deploy 스테이지 (`f0bacb3`)**: `develop` 브랜치 푸시에서만 트리거. `backend`+`infra` rsync 후 서버에서 `docker compose up -d --build backend db redis minio nginx`. 프론트 dist는 해당 test job이 실제 산출물을 냈을 때만 rsync(백엔드 단독 배포가 프론트를 안 건드리게). ai-server는 배포 대상에서 의도적 제외(배포 대상 미정)
- **SHA 태깅 + 롤백 스크립트 (`1d821d7`)**:
  - 백엔드 이미지를 `safy-backend:${DEPLOY_TAG:-latest}`로 빌드 → 과거 배포 이미지가 덮이지 않고 남음
  - deploy job이 `DEPLOY_TAG=$CI_COMMIT_SHORT_SHA` 지정 + 성공 시 `.deployed_tags`에 `<태그> <타임스탬프>` 기록
  - `scripts/rollback.sh`: 서버에서 실행, **재빌드 없이(`up -d`, `--build` 없음)** 이전 태그로 즉시 복귀
  - `.gitattributes`: `*.sh`를 LF로 강제(Windows 체크아웃 → Linux 실행 시 개행 깨짐 방지)
  - **미해결(오늘 못 푼 것)**: 오래된 태그 이미지가 정리되지 않아 Lightsail 디스크가 점점 참 → 추후 prune 정책 필요
- 로컬 배포키 스크래치 폴더 gitignore 처리(`293c418`)

## 3. 오후 — 실전 배포 트러블슈팅

| 시각 | 증상 | 원인 | 해결 | 배운 점 |
|---|---|---|---|---|
| 오후 | 병합 직후 실배포가 `no configuration file provided: not found`로 실패 (`af6d017`) | `--project-directory` 값이 `build: ../backend`의 `..`를 이중 적용 → 컨텍스트가 `Desktop/backend`로 잘못 풀림(로컬 재현) | `--project-directory` 제거, compose 기본값(파일 자기 위치 = `infra/`) 사용. 서버 `.env`를 `~/S15P11A503/.env` → `infra/.env`로 이동, rsync에 `--exclude=.env` 추가(`--delete`로 지워지지 않게), 미배포지만 compose 검증 대상인 `AI_SERVER_PORT`도 서버 .env에 추가 | compose의 상대경로/.env 탐색 기준은 "파일 위치"임을 명확히 인지. 옵션으로 기준을 흔들지 말 것 |
| 오후 | frontend dist 디렉토리에 rsync 시 `Permission denied`로 배포 job 중단(admin sync·compose up·태그 기록이 그 회차에 안 돎) (`130411c`) | nginx volume mount 대상 경로가 호스트에 없을 때 Docker가 **root 소유로 먼저 생성** → ubuntu 유저 rsync가 거부됨 | 즉시 조치: 서버에서 `sudo chown -R ubuntu:ubuntu ...frontend-user ...frontend-admin`. 재발 방지: rsync 전에 ubuntu 유저가 dist **하위 디렉토리까지** `mkdir -p` | 볼륨 마운트 경로는 Docker가 root로 선점할 수 있으니, 배포 유저가 먼저 만들어 소유권을 확보해야 함 |

## 4. 오늘 배운 점
- 배포 자동화의 버그는 대부분 "경로/소유권/기준 디렉토리" 문제 — 로컬에서 컨텍스트를 재현하면 원인이 빨리 좁혀진다
- 이미지 태깅을 커밋 SHA로 남겨두면 "재빌드 없는 즉시 롤백"이 가능해진다(단 디스크 정리 정책은 별도 숙제)

## 5. 내일(7/24) 방향 메모
- 배포 스택 가동 상태를 한 곳에서 보는 모니터링/알림 구성 착수

---

# 2026-07-24 (금) — 서비스 헬스체크 · 로그 · 장애 알림 구성 (S15P11A503-48)

**오늘의 원칙**: 배포가 돌기 시작했으니, "죽었는지 사람이 눈으로 확인"에서 "죽으면 자동으로 알림"으로 관측 수준을 올린다.

## 1. 모니터링/알림 스택 구성 (`88eda1e`)
- 대상: 우리 `docker-compose.yml` 스택(backend/db/redis/minio/nginx). OpenVidu는 자체 관측 스택(grafana/prometheus/loki/alloy)을 이미 가져서 대상 외. AI는 미배포라 모니터만 등록 후 일시정지.
- 추가한 것:
  - `x-logging` 앵커 → 전 서비스에 json-file 로그 로테이션(서비스당 10MB×3=30MB 상한, 디스크 보호)
  - 컨테이너 헬스체크: `db`(`pg_isready`), `redis`(`redis-cli ping`), `nginx`(`wget /healthz`)
  - nginx `/healthz` 전용 엔드포인트(항상 200)
  - **Uptime Kuma** 서비스(`127.0.0.1:3001` 바인딩 → SSH 터널로만 접근, 비공개 유지) + `uptime_kuma_data` 볼륨
  - `backend`/`minio`는 이미지에 curl/mc가 없어 컨테이너 헬스체크 대신 **Kuma가 TCP/HTTP로 감시**하도록 역할 분담
- 안티노이즈 설계: Heartbeat 60s / Retries 3 / Resend off → 배포 재시작(1분 이내)은 걸러지고 **3분 이상 실장애만 1회 알림**
- Mattermost Incoming Webhook으로 알림 연결(웹훅 URL은 비밀값 → Kuma 볼륨에만, 저장소 커밋 금지)
- 상세 설계·모니터 목록·장애 대응 흐름은 `MONITORING.md`로 분리 문서화

> 관측: CI deploy의 `up` 대상 목록에 `uptime-kuma`가 빠져 있었다(당시 `backend db redis minio nginx`만) → 파이프라인만으로는 Kuma가 뜨지 않음. **2026-07-27에 목록에 `uptime-kuma`를 추가해 해소**(해당 날짜 기록 참고).

## 2. 오늘 배운 점
- 이미지마다 들어있는 도구(curl/mc)가 달라 컨테이너 자체 헬스체크가 불가능한 경우가 있음 → 외부 감시자(Kuma)가 TCP/HTTP로 보는 방식으로 우회
- 알림은 상태 전이 시에만 발생하므로, 배포 재시작을 장애로 오인하지 않게 임계값(Retries/Interval)으로 노이즈를 걸러야 실제로 쓸 수 있는 알림이 됨

## 3. 다음 방향 메모
- 2주차 남은 인프라 작업: nginx WebSocket 프록시 + 도메인 루트(Caddy) → 앱 연결로 end-to-end 배포 성립

---

> ℹ️ 위 7/22~7/24 항목은 커밋 이력(`2aa60dd`, `eec628c`, `9fb7bde`, `f110d54`, `76e57e5`, `f0bacb3`, `1d821d7`, `af6d017`, `130411c`, `88eda1e`) 기반으로 재구성한 기록이다. 7/22~7/24의 메모리 실측(`free -h`/`docker stats`)은 커밋에 없어 생략했다.

---

# 2026-07-27 (월) — 모니터링 라이브 검증 + backend 기동 실패 3층 규명

**오늘의 원칙**: 배포 스택이 "실제로 기동되고, 중단되면 알림이 오는지"를 라이브로 검증한다.
검증 중 발견한 실제 장애는 원인을 층별로 좁혀 인프라 범위에서 해결하고, 팀 경계를 넘는
부분(스키마 전략·실 OAuth 자격 증명)은 명확히 넘긴다.

## 1. CI 배포 목록에 uptime-kuma 추가 (7/24 관측 후속)
- 7/24에 남긴 "CI `up` 목록에 uptime-kuma 누락" 관측을 실제로 확인 → `.gitlab-ci.yml` deploy의
  `docker compose up` 대상에 `uptime-kuma` 추가(커밋 후 머지·배포).
- 결과: 서버에 Kuma 컨테이너가 처음으로 기동됨. 그전까지는 compose에 정의만 있고 서버 스택에는
  올라온 적이 없어 SSH 터널(`-L 3001`)이 `Connection refused`로 실패하던 상태였음.

## 2. Uptime Kuma 라이브 검증 (알림 3구간)
- 모니터 6종 등록: backend(TCP 8080), nginx(HTTP `/healthz`), minio(HTTP `/minio/health/live`),
  db(TCP 5432), redis(TCP 6379), ai-server(HTTP `/health`, 일시정지).
- Mattermost Incoming Webhook 생성 후 Kuma 알림에 연결. 세 구간을 순서대로 검증:
  1. 웹훅 → 채널: `curl`로 200 응답 + 채널에 메시지 도달
  2. Kuma Test 버튼 → 채널
  3. 실제 지속 다운 알림: minio를 통제 다운시켜 `[Down]`/`[Up]` 자동 발사 확인
- 함정: 알림을 만들어도 각 모니터에 **연결(enable)** 해야 발사된다. Test는 되는데 실제 다운
  알림이 안 와서 확인해보니 모니터에 알림이 연결되지 않은 상태였음.

## 3. backend 기동 실패 — 3층 규명 (핵심)
`docker compose ps`에 backend가 보이지 않아 규명 착수. `ps -a`로 `Exited (1)` 확인,
`logs`로 원인을 층별로 확인. 한 층을 고치면 다음 층이 드러나는 구조였다.

| 층 | 증상 | 원인 | 해결 |
|---|---|---|---|
| 1 스키마 | `Schema validation: missing table [admin_center_memberships]` → Exited | prod 프로필 `ddl-auto: validate` + 빈 DB + 마이그레이션 도구 부재 → 검증할 표가 존재하지 않음 | 서버 `.env`의 `SPRING_PROFILES_ACTIVE`를 dev(`ddl-auto: update`)로 변경 → 기동 시 표 17개 자동 생성. dev/prod 차이가 ddl-auto뿐이라 다른 부작용 없음을 확인 |
| 2 JWT | `JWT_SECRET must be at least 32 bytes` → Exited | `.env`에는 JWT_SECRET(64자)이 있으나 compose backend `environment:`에 없어 컨테이너로 전달되지 않음 | compose에 JWT_SECRET·AUTH_COOKIE·CORS·OAUTH2 redirect 명시 추가 |
| 3 OAuth | `Client id of registration 'google' must not be empty` → Exited | 2층 수정 때 GOOGLE/KAKAO를 빈 값으로 전달 → application.yml의 `${...:not-configured}` 기본값을 빈 문자열이 덮어씀 | compose에서 `${GOOGLE_CLIENT_ID:-not-configured}` 형태로 지정 → 값이 비면 대체값 사용 |

- 배운 점 요약: `validate`는 표를 만들지 않는다(빈 DB엔 생성 수단이 별도 필요) · `.env`는
  `${}` 치환용일 뿐 컨테이너엔 `environment:`에 나열한 변수만 주입된다 · 빈 문자열을 "설정된 값"
  으로 넘기면 yaml 기본값을 덮으므로 `:-` 기본값으로 방어한다.
- compose 수정을 별도 브랜치 `fix/backend-env-passthrough`(커밋 `ab4cc63`)로 올려 develop
  머지(머지 커밋 `fe329b5`) → CI 배포. `Started SafyApplication in 12.9s`, backend `Up`,
  Kuma `backend service went up!` 알림 수신. 이후 다른 배포(`aae79459`)에도 계속 정상 기동 —
  수정이 git에 있어 배포마다 자동 반영됨을 실증(durability 확인).

## 4. 안티노이즈 (Retries=3)
- 배포 재시작 시 1분 남짓의 블립에도 알림이 발생하는 노이즈 발견(재시도 값이 0이었음).
- 모니터 재시도를 3으로 상향(MONITORING.md §5.1 설계값과 일치). `docker compose restart backend`
  로 검증한 결과, 재시작 블립은 DOWN으로 확정되지 않고 PENDING으로 걸러져 알림이 발생하지 않음.

## 5. Kuma 이벤트 이력이 남긴 인시던트 타임라인 (증거)
- 13:16 오프라인 `getaddrinfo ENOTFOUND backend`(컨테이너 부재) → 15:58 온라인(기동 성공)
  → 16:23 약 1분 블립 `connect ECONNREFUSED`(배포 재시작, JVM 기동 중).
- 메시지 구분: `ENOTFOUND`(이름 해석 실패 = 컨테이너가 없음) vs `ECONNREFUSED`(IP는 있으나
  포트 미개방 = 기동 중). 에러 문구만으로 "컨테이너 부재"와 "기동 중"을 판별 가능.

## 6. 오늘 배운 점
- 기동 실패는 층으로 쌓일 수 있고 각 층은 앞을 고쳐야 다음이 보인다. 로그의 최하단 `Caused by`가
  실제 원인이다.
- 인프라가 풀 수 있는 층(스키마 프로필·env 배선)과 팀이 풀어야 할 층(실 OAuth 자격 증명·Flyway)을
  구분해야 헛돌지 않는다.
- 서버 수동 수정은 rsync 배포(`--delete`)에 덮여 원복되므로, git 커밋 → 배포로 굳혀야 유지된다.
  배포 이미지 SHA가 바뀌어도 계속 기동되는 것으로 이를 실증했다.

## 7. 후속 / 미결
- **Flyway/Liquibase 도입(백엔드)**: 현재 dev/`update`는 스톱갭. 정식 스키마 관리 수단 필요.
- **구글/카카오 실 OAuth 자격 증명(팀)**: 현재 `not-configured`라 소셜 로그인 미동작.
- **seed 데이터 적재**: `backend/src/main/resources/db/seed/*.sql`(행정구역·관제센터·라우팅
  제약) — 제보 라우팅 기능 검증에 필요.
- **2주차 잔여 인프라**: nginx WebSocket 프록시 + 도메인 루트(Caddy) 연결로 end-to-end 성립.
- FE–BE–AI–인프라 통합 인테그레이션 착수 시 위 항목들이 선행 조건이다.
---

# 2026-07-28 (화) — 배포 환경 보안 차단 · 환경변수 배선 완결 · seed 적재

**오늘의 원칙**: 통합 인테그레이션(7/29) 전날. "설정이 있다"가 아니라 "배포 서버에서 실제로
동작한다"를 실호출로 증명하는 것을 완료 기준으로 삼았다.

## 1. DB·Redis가 인터넷에 열려 있었음 (최우선 조치)

포트 바인딩 작업 전 점검 중 발견. 외부(개발 PC)에서 직접 스캔한 결과:

| 포트 | 서비스 | 외부 접근 |
|---|---|---|
| 5432 | PostgreSQL | **OPEN** |
| 6379 | Redis | **OPEN** |
| 8090 | backend | OPEN |
| 8091 | nginx | OPEN (정상) |
| 9200 | MinIO S3 API | OPEN (presign에 필요) |
| 9201 | MinIO 콘솔 | closed |
| 3001 | Uptime Kuma | closed (127.0.0.1 바인딩) |

Redis는 추정에 그치지 않고 외부에서 raw TCP로 PING을 보내 `+PONG` 응답을 확인했다.
compose에 requirepass가 없어 무인증 상태였고, Redis에는 리프레시 토큰(RefreshTokenStore)과
로그아웃 블랙리스트가 저장된다. 사용자가 로그인을 시작하는 순간부터 세션 탈취가 가능한 상태였다.

조치: backend는 도커 네트워크 내부 이름(db/redis)으로 접속하므로 호스트 포트 매핑은
디버깅 용도일 뿐이다. 127.0.0.1 바인딩으로 변경해도 앱 동작에 영향이 없음을 확인하고 적용했다.
MinIO 콘솔(9201)도 외부 미도달 상태였지만 방화벽 정책 변경에 대비해 명시적으로 로컬 바인딩했다.
S3 API(9200)는 브라우저가 presigned URL로 직접 PUT해야 하므로 공개를 유지한다.

## 2. ufw가 못 막은 이유 — Docker의 iptables 우회

`sudo ufw status` 허용 목록에는 22 / 80 / 443 / 8989 / 7881·7885 / 50000-60000 뿐이고
**5432도 6379도 8091도 없다. 그런데 전부 열려 있었다.**

원인은 Docker가 포트를 publish할 때 iptables의 DOCKER 체인에 DNAT 규칙을 직접 써넣기 때문이다.
이 규칙이 ufw의 필터 체인보다 먼저 평가돼서 ufw는 해당 트래픽을 보지도 못한다.
**"ufw를 켰으니 안전하다"가 컨테이너 publish 포트에는 성립하지 않는다.**

실제 외부 노출 여부를 결정하고 있던 것은 ufw가 아니라 SSAFY Lightsail 쪽 방화벽이었다
(9201·3001이 막힌 이유). 우리가 통제할 수 없는 계층에 의존하고 있었던 셈이라, compose의
바인딩 주소로 우리 쪽에서 확정하는 방식으로 바꿨다.

## 3. DB 비밀번호 불일치 — Postgres는 기존 볼륨의 비밀번호를 유지한다

포트 바인딩 변경으로 컨테이너를 재생성하자 backend가 Exited (1).
로그 최하단은 `FATAL: password authentication failed for user "safy"`.

- `.env` / backend 컨테이너 / db 컨테이너의 DB_PASSWORD 해시가 **셋 다 동일**했다.
- 그런데도 인증이 실패했다. 원인은 **db_data 볼륨이 7/23에 만들어졌다**는 것.
- **POSTGRES_PASSWORD는 데이터 디렉토리를 최초 초기화할 때 한 번만 반영된다.** 이후 값을
  바꿔도 볼륨 안의 비밀번호는 그대로다. `.env`를 편집하면서 값이 바뀌었고, 재생성 전까지는
  기존 컨테이너가 옛 값으로 이미 접속해 있어 드러나지 않았다.

조치: `ALTER USER`로 DB를 `.env` 기준에 맞췄다(데이터 0행이라 위험 없음).
psql `-c`는 psql 변수를 치환하지 않으므로 표준입력(`-f -`)으로 넘겨야 한다.

포트 바인딩 변경이 원인은 아니었지만 방아쇠 역할을 했다. **그대로 뒀으면 오늘 밤 CI 배포에서
터졌을 문제**이고, 그때는 배포 변경과 뒤섞여 원인 규명이 훨씬 어려웠을 것이다.

## 4. 검증 방법이 틀려서 두 번 오진했다 (기록 가치 있음)

- **해시 비교 오진**: `printenv | sha256sum`(값+개행)과 `printf %s | sha256sum`(값만)을
  비교했다. 개행 하나 때문에 **항상 불일치**로 나와 정상인 값을 다르다고 판단했다.
- **인증 테스트 오진**: db 컨테이너 안에서 `psql -h 127.0.0.1`로 테스트해 "인증 성공"을 얻었다.
  그러나 postgres 이미지 기본 pg_hba.conf는 127.0.0.1을 **trust**로 처리해 비밀번호를
  아예 검사하지 않는다. **틀린 비밀번호로도 성공이 뜬다.**
  backend는 도커 네트워크로 접속하므로 `docker run --network infra_default`로 같은 경로를
  재현해서야 실패가 드러났다.

교훈: **검증은 실제 사용 경로와 동일한 경로로 해야 한다.** 편한 경로로 확인하면 "확인했다는
착각"만 얻는다.

## 5. 환경변수 배선 누락 4종 — 7/27 2층 문제의 잔여분

7/27에 JWT_SECRET으로 겪은 것과 같은 함정이 남아 있었다. `.env`에 값이 있어도
compose `environment:`에 나열하지 않으면 컨테이너에 전달되지 않는다.

| 변수 | 누락 시 증상 |
|---|---|
| KAKAO_LOCAL_REST_API_KEY | 좌표→행정구역 해석 예외 → 제보가 관제센터에 배정되지 않고 재시도만 반복. 홈 화면 `/locations/resolve`도 실패 |
| SUPER_ADMIN_LOGIN_ID/PASSWORD/DISPLAY_NAME | admin_credentials가 비어 어드민 로그인 자체가 불가능 |
| MINIO_PUBLIC_ENDPOINT | presigned URL이 내부 주소(minio:9000)로 발급 → 브라우저가 닿지 못해 이미지 업로드 100% 실패 |

MINIO_PUBLIC_ENDPOINT는 `.env`에도 없어서 새로 추가했다(`http://i15a503.p.ssafy.io:9200`).
`infra/env.example`은 인증·OAuth·관리자 항목이 통째로 빠져 있어 그대로 복사하면 부팅이
불가능한 상태였고, 존재하지 않는 local 프로필을 권하고 있어 함께 바로잡았다.

**값이 사라질 수 있는 지점이 세 층이라는 것이 이번에 정리됐다**:
① `.env`에 값이 있는가 → ② compose가 컨테이너에 전달하는가 → ③ 앱·저장소가 실제로 쓰는가.
오늘 ②에서 4건(위 표), ③에서 1건(Postgres 볼륨 비밀번호)이 걸렸다.

## 6. nginx 헬스체크가 27시간 동안 거짓말을 하고 있었음

`docker ps`에 nginx가 unhealthy(FailingStreak **3188**, 약 27시간)로 표시됐으나
호스트에서 `/healthz`는 200이었다. 컨테이너 안에서 직접 확인:

- `wget http://127.0.0.1/healthz` → ok
- `wget http://localhost/healthz` → Connection refused

busybox wget이 localhost를 ::1(IPv6)로 먼저 해석하는데 nginx는 IPv4에만 바인딩돼 있어
거부된 것이다. **서비스는 처음부터 정상이었고 헬스체크만 실패하고 있었다.**
127.0.0.1로 변경 후 healthy(FailingStreak 0) 확인. Kuma 알림 노이즈의 원인이기도 했다.

## 7. seed 적재

사전에 ON CONFLICT가 의존하는 제약을 확인(이게 없으면 seed가 실패한다):

- administrative_areas UNIQUE (code)
- control_centers UNIQUE (code)
- control_center_areas UNIQUE (control_center_id, administrative_area_id, coverage_role)

서버의 seed 파일이 저장소와 동일한지 해시로 확인한 뒤 ON_ERROR_STOP=1로 순서대로 적재:
행정구역 → 관제센터(+매핑) → 라우팅 제약.

결과: 행정구역 26(서울시 1 + 자치구 25) · 관제센터 25 · 매핑 25(전부 ACTIVE) ·
부분 유니크 인덱스 2종 생성. 매핑 정합성도 확인(1111000000 종로구 → 종로구 재난안전상황실).

## 8. 배포 환경 실동작 검증

| 검증 | 결과 |
|---|---|
| 어드민 로그인 `POST /auth/admin/login` | **HTTP 200 + accessToken** (배포 서버 최초) |
| 좌표 해석 `/locations/resolve` (경복궁) | **resolved:true, 1111000000, 종로구** |
| 지역명 검색 `/locations/search` | 200, seed 데이터 반환 |
| `/areas/2/status`, `/areas/2/map` | 200, 실데이터 |
| `/reports/recent`, `/administrative-areas/2` | 200 |

좌표 해석 성공이 한 번에 증명한 것: 카카오 키가 컨테이너에 도달했고 · 키가 유효하며 ·
서버에서 카카오 API로 아웃바운드가 열려 있고 · 카카오 응답 코드 형식이 seed와 맞물린다.
**제보 라우팅의 핵심 경로가 뚫렸다.**

미검증: 제보 생성 → 라우팅 → 배정, 이미지 업로드 presign. 둘 다 CITIZEN 계정이 필요한데
시민 계정은 소셜로그인을 거쳐야만 생성되므로 브라우저 없이는 만들 수 없다.
아래 9번이 해결돼야 가능하다.

## 9. 시민 앱이 서버에 붙지 않는 원인 규명 (프론트 영역, 별건)

어드민(:8091/admin/login)은 뜨는데 시민 앱은 로그인 시
`localhost:8080/api/v1/auth/social/google/login`으로 이동해 ERR_CONNECTION_REFUSED.
nginx `/api` 프록시(7/27 수정)는 정상이며 무관하다.

배포된 번들을 직접 grep한 결과 `frontend-user/dist/assets/index-*.js`에
`http://localhost:8080/api/v1`이 **3곳** 박혀 있었다(axios baseURL · SSE URL · 소셜로그인
리다이렉트). `frontend-admin/dist`에는 0건.

원인은 **`.env`의 git 추적 여부** 하나다:

| | .env 커밋 여부 | CI 빌드 시 | 결과 |
|---|---|---|---|
| frontend-admin | 미추적 | .env 없음 → 코드 폴백 `/api/v1` | 같은 출처로 붙어 동작 |
| frontend-user | **추적됨** | .env의 localhost:8080 주입 | 실패 |

Vite는 `.env`를 **빌드 시점**에 읽어 문자열을 번들에 박아 넣는다. 런타임 설정이 아니라서
서버에서 무엇을 고쳐도 이미 구운 번들은 바뀌지 않는다.

수정 대상은 VITE_API_BASE_URL을 직접 읽는 3곳(api/client.ts, api/realtime.ts,
pages/auth/LoginPage.tsx)과 `.env` 추적 해제다. 별도 브랜치로 분리 예정.

## 10. 발견한 배포 함정 — DEPLOY_TAG 미지정

compose가 `image: safy-backend:${DEPLOY_TAG:-latest}`인데 서버에 safy-backend:latest
(24시간 전 빌드)가 남아 있다. 서버에서 DEPLOY_TAG 없이 `docker compose up -d backend`를
실행하면 **현재 운영 이미지가 아니라 낡은 latest로 조용히 롤백된다. 에러도 나지 않는다.**
오늘 서버 작업에는 모두 DEPLOY_TAG를 명시했다. 기본값 제거 여부는 롤백 스크립트가
같은 변수를 쓰므로 별도 판단이 필요하다.

## 11. 오늘 배운 점

- **ufw는 컨테이너 publish 포트를 막지 못한다.** Docker가 iptables에 직접 규칙을 넣기 때문이다.
  컨테이너 노출은 compose의 바인딩 주소(127.0.0.1:)로 통제해야 한다.
- **Postgres는 기존 볼륨이 있으면 POSTGRES_PASSWORD를 무시한다.** 반대로 **MinIO는 root
  자격증명을 매 기동마다 env에서 다시 읽는다.** 같은 "env로 자격증명 주입"이라도 저장 방식이
  달라 변경 절차가 정반대다.
- **검증은 실제 사용 경로로 해야 한다.** 컨테이너 내부 127.0.0.1은 trust라 비밀번호를
  검사하지 않아, 틀린 값으로도 성공이 뜬다.
- **설정값은 세 층에서 유실될 수 있다**(.env → compose 전달 → 앱 사용). 층을 분리해 확인하면
  원인 위치가 바로 특정된다.
- **빌드 시점에 박히는 값과 런타임 값은 다르다.** Vite `.env`는 전자라 서버 수정으로 못 고친다.
  같은 이유로 `.env`를 저장소에 커밋하면 CI 빌드가 그 값을 그대로 굽는다.
- 비밀은 값을 출력하지 않고 **길이·해시·기본값 대조**로 검증할 수 있다. 다만 비교 방식 자체가
  틀리면(개행 혼입) 멀쩡한 값을 불일치로 오판한다.

## 12. 후속 / 미결

- **MINIO_ACCESS_KEY가 기본값(minioadmin)**: 9200이 외부 공개라 스캐너의 1순위 표적이다.
  secret이 32자 랜덤이라 즉시 위험은 아니나 교체 필요. access key는 presigned URL에 평문으로
  들어가는 식별자이므로 랜덤일 필요는 없고, 기본값만 벗어나면 된다.
- **frontend-user baseURL 수정(위 9번)**: 시민 앱 통합의 선행 조건. 별도 브랜치 예정.
- **`/api` 이중 접두어 8곳**: homeApi.ts 3곳은 백엔드에 엔드포인트가 실재하므로 `/api`만
  제거하면 동작한다. notificationsApi.ts 5곳은 백엔드에 알림 API 자체가 없어 팀 결정 필요.
- **백엔드에 없는 엔드포인트**: `/users/me/favorite-locations`, `/faqs`, `/notices`,
  `/inquiries`, `/notifications*`, `/users/me/notification-settings` — 프론트가 뺄지
  백엔드가 만들지 통합 회의에서 결정.
- **행정구역 경계 GeoJSON 부재**: `/areas/{id}/map`의 boundaryGeoJson이 null이다. 지도에
  자치구 경계를 그리려면 필요. seed 추가(백엔드) vs 프론트 자체 보유 중 결정 필요.
- **Flyway 도입(백엔드)**: dev/ddl-auto update는 여전히 스톱갭. 스키마 변경 이력이 남지
  않아 통합 주차에 드리프트 위험이 있다.
- **제보 생성 E2E·presign 실검증**: 시민 계정이 필요해 위 9번 해결 후 가능.
- **배포·복구 런북(티켓 49)**: 이 저널과 별개로 팀원이 따라할 절차서 미작성.

## 2026-07-28 (화) 오후 — 시민 앱 통합: baseURL·OAuth2 프록시·CI 변수

**목표**: 오전 9절에서 원인만 규명한 시민 앱 미연결을 실제로 뚫어 소셜로그인 E2E를 완주한다.

**진행**

- **`frontend-user/.env` 추적 해제 + 코드 폴백**: `.env`가 커밋돼 있어 CI 빌드가 개발용 주소를
  번들에 구웠다. `.env`를 추적에서 빼고 `api/client.ts`에 폴백을 뒀다 —
  `import.meta.env.DEV`면 `http://localhost:8080/api/v1`, 빌드 결과물이면 `/api/v1`.
  `api/realtime.ts`와 `pages/auth/LoginPage.tsx`가 이 값을 공유하도록 정리했다.
  빌드 결과물이 상대경로를 쓰면 nginx와 같은 출처가 되어 주소를 아예 몰라도 된다.

- **nginx에 OAuth2 경로 프록시 추가**: `/oauth2/authorization/{provider}`(로그인 시작)와
  `/login/oauth2/code/{provider}`(제공자 콜백)는 둘 다 `/api/` 밖이라 별도 프록시가 필요하다.
  없으면 SPA 폴백으로 흘러가 `index.html`이 반환되고 화면에 앱의 404가 뜬다(실측).
  `/login/oauth2/`는 SPA의 `/login` 라우트보다 긴 접두어라 우선 매칭된다.

- **`Host $host` → `$http_host`**: `$host`는 포트를 버린다. Spring Security는 이 Host로
  OAuth2 `redirect_uri`를 만드는데 포트가 빠지면 `http://도메인/login/oauth2/code/kakao`가
  되어 콜백이 80번(OpenVidu Caddy)으로 가버린다. 실측으로 확인했다.

- **CI 변수 함정 — 지도가 무조건 실패하던 이유**: 팀원이 추가한 `VITE_KAKAO_MAP_JS_KEY`가
  GitLab CI/CD 변수에 없어서 CI 빌드 번들에 키가 비어 있었다. Vite는 빌드 시점에 값을 굽기
  때문에 서버에서는 고칠 수 없다. CI/CD Variables에 등록(Protect 미체크)해 해결.
  **새 `VITE_` 변수는 코드 머지만으로는 동작하지 않는다.** 변수 등록 후에는
  `frontend-user` 잡 → `deploy` 잡 순서로 재실행해야 한다. deploy만 재실행하면 옛 아티팩트를
  다시 올린다.

- **소셜로그인 E2E 완주**: 카카오 로그인 → 시민 계정 생성 → 앱 진입까지 배포 환경에서
  PC·모바일 모두 성공. `users` 5명. 오전 8절에서 "시민 계정이 없어 불가"로 남겨둔 검증의
  선행 조건이 해소됐다.

**HTTPS가 3건을 동시에 막고 있다는 것을 확인**

| 막힌 것 | 이유 |
|---|---|
| 위치 권한 | 브라우저가 Geolocation을 보안 컨텍스트에서만 허용한다. `http://localhost`는 예외지만 `http://도메인:8091`은 아니다. **좌표가 없으면 lat/lng가 `@NotNull`이라 제보 생성 자체가 불가** |
| 구글 로그인 | 구글은 localhost 외 `http://` redirect URI를 콘솔 등록조차 거부한다 |
| MinIO presign | https 페이지에서 http 업로드 주소는 mixed content로 차단된다 |

세 개가 원인이 하나라서 HTTPS 전환을 다음 날 최우선으로 잡았다.

---

# 2026-07-29 (수) — HTTPS 진입점 전환 · MinIO presign https · 배포 레이스 규명

**오늘의 원칙**: 제안 전에 실측한다. 오늘 계획이 두 번 뒤집혔는데 둘 다 검증 없이 단정한
탓이었고, 프로브 2분으로 판명됐다(12절 참고).

**완료 기준**: `https://i15a503.p.ssafy.io/` 에서 카카오 로그인 → 위치 권한 허용 →
제보 1건 생성(`reports` 0 → 1). 달성했다.

## 1. HTTPS 진입점 — 자체 서명 인증서를 만들지 않았다

주간계획에는 "자체 서명 인증서"로 적어뒀지만, **OpenVidu 스택의 Caddy가 이미 443에서 도메인
정식 인증서(Let's Encrypt)를 쓰고 있었다**. 자체 서명은 브라우저 경고가 남고 구글 OAuth도
통과하지 못한다. Caddy를 경유하는 쪽이 낫다.

더 나아가 **OpenVidu가 이 용도의 확장 지점을 이미 갖고 있었다.**
`/opt/openvidu/config/caddy.yaml` 마지막 라우트:

```yaml
# Custom User Application
# If an app is deployed at CUSTOM_APP_INTERNAL_PORT, it will be served at /*
- dial: "127.0.0.1:${openvidu.CUSTOM_APP_INTERNAL_PORT:-6080}"
```

`MEET_BASE_PATH=/meet` 이라 이 catch-all이 **이미 활성 상태**였다. 6080이 비어 있어서 Caddy가
502를 내고, 502를 `/meet`으로 302시키는 에러 라우트가 그걸 삼키고 있었다. 실측:

```
https://…/            → 302 https://…/meet/
https://…/healthz     → 302 https://…/meet/healthz
https://…/api/v1/areas → 302 https://…/meet/api/v1/areas
```

**즉 6080에 리스너를 두는 것만으로 도메인 루트가 우리 앱이 된다.** 방법을 두 개 비교했다.

| | `openvidu.env`의 `CUSTOM_APP_INTERNAL_PORT`를 8091로 변경 | **nginx가 6080도 리스닝** |
|---|---|---|
| OpenVidu 스택 재시작 | 필요(`down`+`up`, 라이브 WebRTC 중단) | 불필요 |
| OpenVidu 업그레이드 | 값이 덮어써짐 | 무관 |
| git 추적 | 안 됨(서버 드리프트) | 됨 |
| Caddy 설정 파일 수정 | — | 없음 |

후자를 택했다. compose의 nginx에 `127.0.0.1:6080:80` 한 줄이 전부다. `127.0.0.1` 바인딩이라
6080은 인터넷에서 닿지 않고 같은 호스트의 Caddy만 쓴다.

**배포 전에 임시 리스너로 실증했다.** 6080에 헤더를 그대로 되돌려주는 파이썬 프로세스를
10초간 띄웠다. `/`와 `/app/report` 둘 다 그 프로브에 도달했고, 그 시간 동안 OpenVidu 경로는
`/meet` 301 · `/dashboard` 200 · `/health/caddy` 200 · `/grafana` 302로 정상이었다.
설정 파일만 읽고 추론한 상태로 배포하지 않은 것이 이 작업에서 가장 잘한 판단이다.

경로 충돌도 확인했다. Caddy가 점유한 접두어는 `/meet`, `/dashboard`, `/grafana`,
`/minio-console`, `/openvidu/*`, `/whip`, `/twirp`, `/rtc`, `/agent`, `/health/caddy` 뿐이고,
우리 앱의 `/app/*`, `/admin/*`, `/login`, `/signup`, `/oauth/callback`, `/api/*`와 겹치지
않는다. 어드민이 `/admin`인 것이 다행이다 — `/dashboard`로 옮기면 OpenVidu 화면에 가려진다.

프론트는 수정이 필요 없었다. 배포 번들이 `baseURL`로 상대경로를 쓰기 때문이다(7/28 오후 작업의
결과다). `frontend-user`는 `` var el=`/api/v1` ``, `frontend-admin`은 `` var $c=`/api/v1` ``,
양쪽 모두 `i15a503`·`8091` 리터럴 0건.

## 2. Caddy는 TLS를 layer4에서 종료한다 — 이 사실이 계획 하나를 폐기시켰다

임시 리스너가 받은 헤더:

```
Host: i15a503.p.ssafy.io
Via: 1.1 Caddy
X-Forwarded-Host: i15a503.p.ssafy.io
X-Forwarded-Proto: http          ← https가 아니다
X-Forwarded-For: 127.0.0.1       ← 실 클라이언트 IP가 아니다
```

`caddy.yaml`의 구조를 보면 이유가 명확하다. Caddy는 `layer4` 앱에서 TLS를 종료하고 **평문으로**
`127.0.0.1:5656`(tcp-demux) → `127.0.0.1:7880`(http 서버)로 넘긴다. HTTP 서버가 보는 연결은
localhost에서 온 평문 http이므로 `X-Forwarded-Proto`에 `http`를 쓴다.

**파급 1 — 계획 폐기.** 원래는 nginx가 `X-Forwarded-Proto`를 통과시키고 백엔드에
`server.forward-headers-strategy: FRAMEWORK`를 넣어 Spring이 https를 인식하게 하려 했다.
그런데 통과시킬 값 자체가 `http`이므로 **효과가 0이다.** 백엔드 파일을 건드리는 계획을 버렸다.

제대로 하려면 nginx가 `X-Forwarded-Proto`를 **정적으로** `https`로 박아야 하는데, 8091과
6080이 컨테이너 내부에선 둘 다 포트 80이라 구분할 수 없다. 컨테이너 포트를 나누거나 8091을
폐기해야 하는 별건 작업이다.

**파급 2 — 실 클라이언트 IP 소실.** layer4 프록시가 PROXY protocol 없이 dial하므로 뒤에서는
전부 `127.0.0.1`로 보인다. 전환 전 nginx 로그에는 `211.234.192.138` 같은 실 IP가 찍혔지만
전환 후에는 사라졌다. 접근로그·남용추적·레이트리밋에 영향이 있다. 복구하려면 Caddy 설정을
고쳐야 해서 별건으로 남긴다.

**결과적으로 `redirect_uri`는 http로 생성된다.** 배포 전에 8091에 `Host: i15a503.p.ssafy.io`를
실어 보내 실측했다:

```
Host: i15a503.p.ssafy.io      → redirect_uri=http://i15a503.p.ssafy.io/login/oauth2/code/kakao
Host: i15a503.p.ssafy.io:8091 → redirect_uri=http://i15a503.p.ssafy.io:8091/login/oauth2/code/kakao
```

카카오는 http 등록을 허용하고, 콜백이 80번으로 와도 Caddy가 443으로 301시키므로 로그인은
완주한다. **구글은 이 상태로는 안 된다** — 구글 OAuth 작업 시 `redirect_uri`를 명시 지정하는
방식으로 함께 정리해야 한다.

같은 요청에서 `code_challenge_method=S256`을 확인했다. **PKCE가 켜져 있어** authorization
code가 평문 한 홉을 지나가도 `code_verifier` 없이는 교환할 수 없다. 이 사실이 "http 콜백을
감수한다"는 판단의 근거가 됐다. HSTS 헤더는 없어서 브라우저가 자동 업그레이드하지도 않는다.

## 3. 동시 배포 레이스 — 늦게 끝난 파이프라인이 이긴다

배포가 성공했는데 6080이 붙지 않았다. nginx는 1분 전에 재생성됐는데 포트가 없었다.

```
git 조상관계: 68739eb ─┬─ e09c696(내 104) ──► 742951b  (develop HEAD, 전부 포함)
                       └─ 8edee96(팀원 117) ──► c361526  (742951b의 조상)

배포 완료:   742951bd  02:11:09Z
             c361526c  02:12:24Z   ← 1분 늦게 끝났고, 이쪽이 이겼다
```

`c361526` 시점 소스에는 뒤 커밋의 `infra/` 변경이 **물리적으로 없다.** 두 파이프라인이 동시에
돌면서 늦게 끝난 쪽의 `rsync --delete`가 서버의 `docker-compose.yml`을 되감았다.
`env.example`도 같이 되돌아갔다(`AUTH_ACCESS_TOKEN_TTL` 0건으로 확인).

증상이 헷갈린 이유는 **동기화 방식의 비대칭**이다.

| 대상 | 방식 | 결과 |
|---|---|---|
| `backend`, `infra` | 항상 `rsync --delete` | 그 커밋 시점으로 되감김 |
| `frontend-*/dist` | 빌드 잡이 `changes:` 조건부 → 아티팩트 있을 때만 | 갱신 안 돼서 멀쩡했다 |

`infra`만 되감기고 프론트는 정상이라 "배포가 실패한 것"처럼 보이지 않았다.

조치: `deploy` 잡에 `resource_group: production`을 넣어 프로젝트 전역에서 한 번에 하나만
실행하게 했다. **다만 이건 상호배제만 주고 실행 순서는 보장하지 않는다.** resource group의
기본 `process_mode`가 `unordered`이고, 순서까지 잡으려면 API 설정이 필요하다:

```
PUT /projects/:id/resource_groups/production   {"process_mode": "oldest_first"}
```

이건 `.gitlab-ci.yml`에 담을 수 없어 주석에 절차만 남겼다.

**운영 함정 하나 더**: 과거 파이프라인의 `deploy` 잡을 재실행하면 **그 커밋 시점으로 되감긴다.**
여러 MR이 몰리는 중에는 재실행 대신 develop HEAD에서 신규 실행해야 한다. `resource_group`은
동시 실행은 막지만 사람이 옛 파이프라인을 수동 재실행하는 것까지 막아주지 않는다.

**같은 날 실전 검증됨**: `f92a0b16`(06:11:29Z)과 `aeacc69a`(06:11:56Z)가 27초 간격으로
순차 실행됐고, 뒤 커밋이 앞 커밋의 자손이라 되감김이 없었다.

## 4. MinIO presign을 https로 — nginx가 TLS를 종단한다

HTTPS 전환 후 이미지 제보가 조용히 실패했다. 화면상 제보는 등록되는데 "첨부 이미지 없음"이다.

```
① presign 발급   POST /api/v1/reports/1/images/presigned-upload → 200
② DB            media_assets.storage_status = PENDING_UPLOAD (file_size 비어있음)
③ MinIO          /data/safy-reports → 완전히 비어있음
```

presign은 정상 발급됐는데 **PUT이 도달한 흔적이 아예 없다.** 앱은 https, presign 대상은
`http://i15a503.p.ssafy.io:9200` → 브라우저가 요청을 **보내기 전에** 막았다.

**MinIO 자체에 TLS를 붙이지 않았다.** 그러면 내부 통신(`backend → minio:9000`)까지 https가
되는데 인증서는 `i15a503.p.ssafy.io`용이라 호스트명 `minio`와 맞지 않아 검증에 실패한다.
도커 네트워크 alias로 우회할 수 있지만 불필요하게 복잡하다.

대신 nginx가 앞에서 종단한다. 이걸 가능하게 한 것은 **백엔드가 `MinioClient`를 두 개로
나눠둔 구조**다.

```java
minioClient    ← minio.endpoint         내부 통신 (bucketExists/statObject/removeObject)
presignClient  ← minio.public-endpoint  presign URL 문자열 생성만, 네트워크 통신 없음
```

`getPresignedObjectUrl()`은 순수 계산이라 공개 주소만 바꾸면 되고 **내부 구간은 평문 그대로
둘 수 있다.** 이 분리가 없었으면 인증서나 네트워크까지 손봐야 했다.

```
브라우저 ──https──► 호스트 9200 (nginx, 컨테이너 9443 TLS) ──평문──► minio:9000
```

**포트는 9200을 그대로 쓴다.** AWS 콘솔 권한이 없어 새 포트를 열 수 없으므로 이미 열린 포트를
재사용해야 한다. `minio`의 직접 `9200:9000` 바인딩은 제거했다 — 양쪽에서 바인딩하면 충돌한다.

**서명 호환이 이 작업의 핵심이다.** presigned URL은 host와 경로를 함께 서명하므로 MinIO가 같은
값으로 재계산해야 일치한다.

- `Host`를 `$http_host`로 넘긴다 — `$host`는 포트를 버려서 `:9200`이 빠진다(7/28 오후에 같은
  변수로 OAuth가 깨졌던 것과 동일한 함정이다)
- `proxy_pass` 뒤에 경로를 붙이지 않는다 — 붙이면 접두어가 잘려 서명이 깨진다

`client_max_body_size 20m`을 명시했다. 이미지가 장당 10MB·최대 5장인데 **nginx 기본값은 1MB**라
그대로 두면 업로드가 413으로 죽는다.

**인증서는 Caddy가 관리하는 것을 읽기 전용으로 공유한다.**

```
/opt/openvidu/data/caddy_data/certificates/acme-.../i15a503.p.ssafy.io/
  i15a503.p.ssafy.io.crt   4828B  -rw------- root:root
  i15a503.p.ssafy.io.key    227B  -rw------- root:root   (디렉터리도 drwx------)
```

**파일이 아니라 디렉터리를 마운트한다.** 갱신 시 파일이 교체되면 파일 단위 바인드 마운트는
옛 inode를 계속 가리켜 새 인증서가 보이지 않는다. 갱신 후에는 nginx reload가 필요하다.
컨테이너의 root가 600 파일을 읽을 수 있어 권한 문제는 없었다.

**배포 전 격리 검증**(임시 컨테이너 + `127.0.0.1:19443`, 운영 nginx 무접촉). 인증서 마운트가
실패하면 nginx가 기동하지 못해 앱 전체(6080·8091)가 죽으므로 먼저 확인했다.

```
nginx -t                       → 성공, 컨테이너 안에서 인증서 읽기 가능
GET /minio/health/live         → 200, ssl_verify_result=0
GET /safy-reports/nonexistent  → MinIO가 BucketName=safy-reports, Key=nonexistent,
                                 Resource=/safy-reports/nonexistent 로 정확히 파싱
```

마지막 항목이 **경로 무손실 = 서명 호환 조건 충족**의 증거다. 배포 후에는 `mc`로 실제
presigned GET을 만들어 200과 내용 일치까지 확인했다. 브라우저에서도 갤러리 업로드 7건이
`AVAILABLE`로 전이했고 그중 2.9MB 건이 `client_max_body_size` 설정을 증명한다.

이 작업은 시민 앱의 업로드와 **어드민의 이미지 조회를 동시에** 풀었다. 어드민도 같은
`MINIO_PUBLIC_ENDPOINT` 기준으로 presigned GET을 받기 때문이다.

## 5. `ddl-auto: update`는 CHECK 제약을 갱신하지 않는다

팀원이 `realtime-event-type-constraint.sql` seed를 추가했다. 적재 전 실측:

```
CHECK 제약: 5개  (REPORT_ASSIGNED, IMAGE_READY, LIVE_READY, LIVE_ACTIVE, REPORT_MESSAGE)
enum:      12개
```

Hibernate의 `ddl-auto: update`는 컬럼·테이블은 추가하지만 **기존 CHECK 제약은 절대 수정하지
않는다.** 테이블이 처음 만들어진 뒤 enum에 값이 추가되면 제약만 옛 상태로 남아, 새 이벤트를
INSERT하는 순간 제약 위반으로 트랜잭션이 롤백된다. `REPORT_REASSIGNED`를 쓰는 코드는 이미
배포돼 있었으므로 재배정·업무종료·반려·취소가 전부 500이 날 상태였다.

적재해서 12개로 맞췄다. 값을 넓히기만 하므로 안전하고 멱등하다.
7/27 이후 유지 중인 `dev`/`ddl-auto: update` 스톱갭이 실제로 물어뜯은 첫 사례다.

## 6. `AUTH_COOKIE_SECURE`와 8091의 역할 이전

`AuthController`의 쿠키 빌더는 세 속성을 각각 다른 위협에 대응시킨다.

```java
ResponseCookie.from("SAFY_REFRESH_TOKEN", value)
    .httpOnly(true)                        // JS가 못 읽음   → XSS      이미 켜짐
    .secure(properties.cookieSecure())     // http에 안 붙음 → 도청     꺼져 있었음
    .sameSite("Lax")                       // 타사이트 차단  → CSRF     이미 켜짐
    .path("/api/v1/auth").maxAge(14일)
```

액세스 토큰은 프론트의 메모리 변수(`let accessToken: string | null = null`)라 새로고침하면
사라진다. 그러면 `POST /auth/token/refresh`가 이 쿠키로 재발급받는다. **즉 이 쿠키 하나가
14일짜리 세션 전체다.** 전환 전 nginx 로그에 http로 오간 refresh 요청이 74건 있었고 그중
200 응답도 있었다 — 유효한 리프레시 토큰이 평문으로 왕복한 기록이다.

`true`로 켰다. 8091에서는 두 단계 모두 막힌다 — Spring이 http 응답에 `Secure` 쿠키를 보내면
브라우저가 **저장을 거부**하고, https에서 받은 쿠키도 http 요청에 **실리지 않는다.**
증상은 "로그인은 되는 것처럼 보이는데 새로고침하면 로그아웃"이다.

**팀원 접속 불가 문의가 실제로 발생했다.** 원인이 셋으로 갈릴 수 있어 분기해서 판단했다.

| 증상 | 원인 |
|---|---|
| 로그인 화면 자체가 안 뜸 | 배포 중 순단 |
| 화면은 뜨는데 로그인 실패(401) | **계정이 없음** — 어드민 로그인 가능 계정은 `superadmin` 하나뿐 |
| 로그인 후 이동·새로고침에서 튕김 | **8091 주소 사용** |

세 번째였다. nginx 로그의 referer가 `http://i15a503.p.ssafy.io:8091/admin/login`이었다.
`curl`로는 8091도 200이 나오는데, **API는 정상이고 쿠키를 버리는 건 브라우저**라서 CLI로는
잡히지 않고 브라우저에서만 드러난다.

주소가 바뀌었다는 것을 팀에 공지해야 한다. 8091은 포트를 닫지는 않았고 `healthz`·디버그용으로
남긴다. 실제로 닫는 것은 Kuma 모니터 대상 확인 후 별건이다.

```
시민 앱   https://i15a503.p.ssafy.io/app
어드민    https://i15a503.p.ssafy.io/admin/login
healthz   http://i15a503.p.ssafy.io:8091/healthz   (디버그용 유지)
```

## 7. 카카오 콘솔 구조

두 번 헤맸다. 기록해둘 만하다.

- **JavaScript SDK 도메인은 origin 단위(스킴+호스트+포트)로 검사하고 경로는 보지 않는다.**
  그래서 `https://i15a503.p.ssafy.io` 한 줄이 시민 앱(`/app`)과 어드민(`/admin`)을 둘 다
  커버한다. 반대로 `http://…:8091`은 https를 커버하지 못해 별도 항목이 필요하다.
  어드민도 카카오맵을 쓰는데(`components/admin/ReportMap.tsx`) 같은 JS 키(`1c288b60…`)라
  추가 등록이 필요 없었다.
- **로그인 Redirect URI는 REST API 키 쪽에 있다.** `카카오 로그인 > 일반`에는 없고,
  `앱 설정 > 플랫폼 키 > Default Rest API Key`의 "카카오 로그인 리다이렉트 URI"다.
  백엔드가 `client_id`로 REST 키를 쓰므로 카카오는 그 키에 등록된 목록과 비교한다.
  JS 키 페이지의 같은 이름 필드가 비어 있어도 로그인이 동작하는 이유다.
- `카카오 로그인 > 고급`의 "리다이렉트 URI"는 **로그아웃** 리다이렉트다. 이름이 같아
  혼동했다. 도움말 문구를 읽어야 구분된다.

전환 중에는 기존 `http://…:8091/...` 항목을 지우지 않고 **추가만** 했다. 롤백 경로를 남겨두는
편이 안전하고, 우리 서버 자신의 주소라 화이트리스트에 남겨도 위협이 아니다.

## 8. 무중단 배포는 지금 하지 않는다

실측 다운타임:

```
backend 부팅        13.1초 (Started SafyApplication in 13.124 seconds)
502 발생 구간       배포 시점 기준 약 15초
502 총 27건 — 대부분이 /api/v1/realtime/events
```

이미지 빌드는 컨테이너 교체 전에 끝나므로 실제 끊김은 재생성 + Spring 부팅 = **약 15초**다.
502 건수는 SSE 클라이언트가 3초마다 재연결해서 부풀어 있다.

무중단의 전제조건이 두 개 없다.

1. **헬스 엔드포인트가 없다.** 인증 없이 200을 주는 엔드포인트가 없어 Kuma도 TCP 포트 열림으로만
   감시한다. 포트가 열렸다는 건 Spring이 다 뜬 것과 다르다. 이 상태로 롤링을 하면 준비 안 된
   인스턴스로 트래픽이 가서 오히려 에러가 늘어난다.
2. **`ddl-auto: update` 스톱갭이다.** 두 버전이 동시에 뜨면 스키마 충돌이 가능하다.
   5절이 정확히 그 계열의 사고다.

우리 배포는 OpenVidu 스택을 건드리지 않으므로 **진행 중인 WebRTC 세션은 살아남는다.**
가장 아까울 부분이 이미 안전하다.

전제조건 없이 롤링부터 붙이면 순서가 거꾸로다. 헬스 엔드포인트(백엔드 요청 필요)와 배포 알림이
먼저다. 배포 알림은 Kuma에 Mattermost가 이미 붙어 있어 비용이 작고, 오늘 팀원 혼선을 직접
막는다.

## 9. CI 캐시 오설정 — `npm ci`와 `node_modules`는 같이 쓸 수 없다

프론트 변경의 MR→배포가 10분 이상 걸린다는 관측에서 시작했다. 빌드 자체는 로컬에서 3.41초다.

```
frontend-user:
  cache:
    paths:
      - frontend-user/node_modules   # ← 캐시 대상
  script:
    - npm ci                          # ← node_modules를 지우고 시작한다
```

`npm ci`는 설계상 기존 `node_modules`를 **삭제한 뒤** 설치한다. 그래서 매 실행마다 캐시를
내려받아 압축을 풀고, `npm ci`가 그걸 전부 지우고, 네트워크에서 다시 설치하고, 캐시를 다시
올린다. **이득이 0인데 양쪽에서 시간만 쓴다.** `node_modules` 대신 npm의 다운로드 캐시
(`.npm`)를 캐시하고 `--prefer-offline`을 붙이는 것이 맞다.

큐도 크다. 파이프라인 `#158575`(deploy 잡 하나)가 **실행 73초, 대기 119초**였다. 스테이지가
`test` → `deploy` 순차라 잡마다 큐를 따로 서므로 프론트 변경은 큐를 두 번 낸다. 공용 러너라
이 부분은 통제 밖이고, 그래서 프론트 변경의 하한은 4~5분이다.

`changes:` 조건부 규칙 덕분에 `infra/`만 바꾼 배포는 `test` 스테이지가 통째로 스킵되어 머지
10초 뒤에 배포가 시작된다. 같은 규칙이 3절의 비대칭 원인이기도 하다.

**개선 폭은 측정하지 못했다.** GitLab 잡 로그의 `npm ci` 구간 시간을 봐야 판단할 수 있다.
30초면 손댈 가치가 있고 5초면 큐가 지배적이라 무의미하다.

## 10. 관제 흐름이 OPERATOR 계정 부재로 전면 정지 상태다

제보 18건이 쌓였는데:

```
report_operator_assignments = 0
realtime_events             = 0
role별 사용자                CITIZEN 6 / SUPER_ADMIN 1 / OPERATOR 0
어드민 로그인 가능 계정        superadmin 하나
```

담당자가 없어 배정이 안 되고, 배정이 없으니 이벤트가 발생하지 않는다. 결과적으로
관제 대시보드가 빈 목록이고, 5절에서 넓혀둔 CHECK 제약은 아직 한 번도 쓰이지 않았으며,
**SSE 검증도 불가능하다**(관측할 이벤트가 없다).

팀원이 어드민을 쓰려면 `superadmin` 하나를 공유해야 하는 상태다.
팀원이 추가한 `operator-accounts.sql`(관제센터 25곳 × 2명 = 50계정)이 이걸 푸는데,
비밀번호가 `Operator1234!` 공통이고 파일 주석에 "운영 환경에서 실행하지 마세요"가 적혀 있다.
서버가 이제 https로 정식 노출된 상태라 **적재 여부는 팀 결정이 필요하다.**

이 하나가 티켓 95(WebRTC·Egress 검증), SSE 원인 규명, 관제 대시보드를 동시에 막고 있다.

## 11. 카메라 미리보기 검은 화면 (프론트, 별건)

인프라 원인이 아님을 먼저 배제했다. 응답 헤더에 `Permissions-Policy`·`Feature-Policy`·CSP가
전부 없고 https 보안 컨텍스트도 정상이라 `getUserMedia`를 막는 요소가 없다.

원인은 React ref 타이밍이었다. `<video>`는 `cameraStatus === 'ready'`일 때만 렌더되는데
`srcObject` 대입이 그 상태 변경 **이전**(아직 `'connecting'`)에 일어난다. 그 시점
`videoRef.current`는 반드시 `null`이라 대입이 조용히 스킵되고, 이어서 `<video>`가 마운트되지만
`srcObject`가 비어 있다. 첫 촬영에서 100% 재현된다. `capturePhoto`의 `video.videoWidth === 0`
검사에 걸려 "카메라 화면이 아직 준비되지 않았습니다"가 뜬다.

`retakePhoto`는 `capturedPhoto`만 지우고 스트림은 유지하므로 `다시 촬영`도 같은 이유로
깨져 있었다. 엘리먼트가 마운트된 뒤에 붙이는 `useEffect` 하나로 두 경로를 함께 고쳤다.

같은 폴더의 `LiveReportPage`는 `<video>`를 조건 밖에서 항상 렌더하고 패널을 위에 덮는 구조라
이 버그가 없다. **정답 패턴이 이미 옆 파일에 있었다.**

## 12. 오늘 배운 점

- **제안 전에 실측한다.** 오늘 계획이 두 번 뒤집혔다. `forward-headers-strategy`가 필요하다고
  단정했는데 Caddy가 주는 `X-Forwarded-Proto`가 `http`여서 무의미했고(2절), SSE가 60초
  타임아웃으로 끊긴다고 인과를 만들었는데 `@EnableScheduling`과 15초 heartbeat가 있어
  성립하지 않았다. **둘 다 프로브 2분에 판명됐다.** 설정 파일을 읽고 세운 가설을 발견처럼
  말하면 계획 전체가 그 위에 쌓인다.
- **성공 기준을 먼저 쓴다.** "제보 1건 생성"을 세 번째 응답에서야 정했다. 처음에 썼다면
  operator seed·SSE·CORS는 "이거 없으면 기준이 실패하나?"에서 즉시 탈락했을 것이다.
  범위가 먼저 부풀고 기준이 나중에 따라오면 순서가 거꾸로다.
- **같은 함정이 다른 옷을 입고 다시 온다.** `$host`가 포트를 버리는 문제는 7/28 오후에 OAuth
  `redirect_uri`를 깨뜨렸고, 오늘 MinIO presign 서명에서 똑같이 나타났다. 프록시가 Host를
  어떻게 넘기는지는 **서명·리다이렉트가 걸린 모든 경로에서 확인해야 한다.**
- **되돌리기의 층위를 구분해야 한다.** `rollback.sh`는 backend 이미지만 되돌린다.
  `docker-compose.yml`·`nginx/*.conf`는 git → rsync로 오므로 그 스크립트로 되돌릴 수 없고,
  프론트 `dist`는 서버에서 버전 관리조차 안 된다.
- **`--delete` 동기화는 시간 여행이다.** 과거 파이프라인 재실행이나 동시 실행은 서버를 그
  커밋 시점으로 되감는다. 배포 파이프라인에 동시성 가드가 없으면 커밋 순서와 배포 결과 순서가
  달라진다.
- **아키텍처가 해결 가능성을 결정한다.** 백엔드가 `MinioClient`를 내부용·presign용으로
  나눠둔 덕분에 내부는 평문, 외부만 https로 갈 수 있었다. 합쳐져 있었으면 인증서
  호스트명 문제를 정면으로 풀어야 했다.
- **미완성 기능이 기본 선택지면 위험하다.** 제보 화면의 기본 선택이 "실시간 영상 제보"인데
  해당 티켓(94)은 미할당·Low다. 시민이 처음 만나는 경로다.

## 13. 후속 / 미결

- **OPERATOR 계정 seed 적재 여부(팀 결정)**: 10절. 티켓 95·SSE 검증·관제 대시보드가 여기에
  걸려 있다. 공개 비밀번호 50계정을 https로 노출된 서버에 넣는 판단이 필요하다.
- **비밀 교체**: `MINIO_ACCESS_KEY`가 여전히 기본값 `minioadmin`이고 presigned URL의
  `X-Amz-Credential`에 그대로 노출된다. 카카오 클라이언트 시크릿도 화면 공유 중 노출됐다.
  `.env` 편집 + 재기동을 한 번에 묶으면 비용이 작다. MinIO 키는 minio·backend를 **동시에**
  재기동해야 한다.
- **헬스 엔드포인트(백엔드)**: 8절. 무중단·롤링의 전제이면서 Kuma를 TCP에서 HTTP 감시로
  바꾸는 즉시 효과도 있다.
- **배포 알림**: Kuma에 Mattermost가 이미 연결돼 있다. 배포 시작·종료를 알리면 6절의
  혼선이 재발하지 않는다.
- **CI 캐시 개선**: 9절. `npm ci` 구간 시간 측정이 선행 조건이다.
- **8091 폐기**: Kuma 모니터 대상과 팀 북마크 확인 후. 폐기 시 카카오 콘솔의
  `http://…:8091` 항목 두 개(JS SDK 도메인 / REST 키 리다이렉트 URI)도 함께 정리한다.
- **SSE 원인 규명**: nginx 로그에 짧은 재연결이 보이지만 원인을 특정하지 못했다. heartbeat가
  15초이므로 `proxy_read_timeout` 60초 가설은 성립하지 않는다. 401 재발급과 페이지 이동이
  더 그럴듯하다. 10절이 풀려야 관측이 가능하다.
- **X-Forwarded-For 실 IP 복구**: 2절. Caddy layer4에 PROXY protocol이 필요해 OpenVidu 설정
  수정을 감수해야 한다.
- **구글 OAuth**: GCP 콘솔 접근 권한이 없다. `redirect_uri` 명시 지정 방식과 함께 처리한다.
- **인증서 갱신 시 nginx reload**: 4절. Caddy가 갱신해도 nginx는 기존 인증서를 물고 있다.
  런북 항목.
- **`resource_group` process_mode**: 3절. API 설정이라 저장소에 담기지 않는다.
- **AI·DATA 배포**: Dockerfile이 아직 없다. AI 학습·테스트가 7/31까지 예정이라 배포는
  8/1~8/3이 현실적이고, 그 뒤 부하 측정(티켓 106)과 대응 여유가 필요하다. 컨테이너화 뼈대는
  AI 코드가 바뀌어도 미리 잡아둘 수 있어 임계 경로에서 뺄 수 있다.
- **배포·복구 런북(티켓 49)**: 여전히 미작성. 오늘만으로도 진입점·MinIO 위치·9200 소유·8091
  의미가 바뀌었고 8091 폐기·비밀 교체·헬스 엔드포인트·AI 배포가 예정돼 있다. **절차서는
  현재 상태를 기술하므로 스택이 멈춘 뒤에 쓰는 것이 맞다.** 이 저널(시점 기록)과 역할이 다르다.

---

# 2026-07-30 (목) — 통합 로그 수집(티켓 104 종료) · WebRTC/Egress 원인 규명

> 1~3절(통합 로그)은 7/29 오후 작업이고 배포는 `2026-07-29T07:11Z`다. 4절부터가 7/30이다.
> 인용한 타임스탬프는 전부 UTC 원문이라 KST 기준 날짜와 다르게 보일 수 있다.

## 1. 통합 로그 수집은 라벨 하나로 끝났다 (`caa2d7d`, 티켓 104)

서버에 Loki·Grafana·Prometheus·Alloy가 이미 떠 있다. OpenVidu 스택이 자체 관측 도구를
포함하기 때문이다. 문제는 우리 컨테이너만 거기서 빠져 있었다는 것이고, 원인은 Alloy의
수집 대상 선정 방식이었다.

```
discovery.docker { filter { name = "label"  values = ["logging=true"] } }
```

**컨테이너 이름이 아니라 `logging=true` 도커 라벨로 고른다.** `openvidu`·`caddy`·`egress`
등에는 이 라벨이 있고 `infra-backend-1`·`infra-nginx-1`에는 없었다. 그래서
`docker-compose.yml`에 `labels: - "logging=true"`를 붙이는 것만으로 기존 Grafana에
합류했다. OpenVidu 설정은 건드리지 않았다.

배포 파이프라인을 태우기 전에 라벨만으로 수집되는지를 일회용 컨테이너로 먼저 확인했다.

```
docker run -d --name loki-probe --label logging=true alpine \
  sh -c 'while true; do echo SAFY-LOKI-PROBE-$(date +%s); sleep 3; done'
→ 25초 안에 Loki에서 조회됨. 확인 후 삭제.
```

부수 확인 두 가지.

- Loki가 `service_name`·`detected_level`을 자동으로 붙인다. 별도 작업이 필요 없다.
- **Alloy의 relabel 규칙은 컨테이너 이름 하나만 Loki 라벨로 옮긴다.** `stack=safy` 같은
  커스텀 라벨을 붙여도 조회에 쓸 수 없다. 우리 서비스는 `{container=~"infra-.*"}`로 고른다.

`db`·`redis`·`minio`·`uptime-kuma`는 티켓 범위 밖이라 라벨을 붙이지 않았다. 라벨 변경은
컨테이너 재생성을 유발하므로 불필요한 재기동을 피하는 쪽이 낫다. 실제로 이번 배포에서
재생성된 것은 `backend`와 `nginx` 둘뿐이다.

조회 방법·쿼리·재현 절차는 `LOGGING.md`로 분리했다(`5125116`).

## 2. Spring Boot는 요청별 로그를 남기지 않는다

통합 로그의 실효 범위를 마커로 확인했다.

```
curl "http://127.0.0.1:6080/api/v1/reports?probe=CORRTEST-1785309229"
→ infra-nginx-1 에만 기록됨. infra-backend-1 에는 없음.
```

따라서 정상 요청의 nginx ↔ backend 연결은 **타임스탬프로만** 맞출 수 있다. 예외가 나면
backend가 스택트레이스를 남기므로 실패 경로 추적에는 지장이 없다. 요청 ID 단위 추적이
필요해지면 backend에 access 로그나 MDC trace ID를 켜야 하고, 이는 백엔드 영역이다.

프론트는 SPA라 서버 로그가 없다. 프론트 계층에서 볼 수 있는 것은 nginx access 로그가
전부다. 브라우저 콘솔을 Loki로 보내려면 프론트에 리포팅 SDK가 필요한데, 제출까지 남은
기간에 새 의존성을 들일 값어치는 없다고 판단했다.

## 3. Loki 3100은 무인증이고 보안그룹만이 막고 있다

`auth_enabled: false`이고 host 네트워크로 `0.0.0.0:3100`에 바인딩돼 있다. 외부에서
접속하면 타임아웃이므로 지금은 닫혀 있지만, **막고 있는 것은 컨테이너 설정이 아니라
보안그룹 하나뿐이다.** 3100이 열리는 순간 전 서비스 로그가 무인증으로 노출된다.
OpenVidu 소유 설정이라 지금은 바꾸지 않되, 포트 정책을 손댈 일이 생기면 먼저 볼 항목이다.

## 4. WebRTC는 이미 동작하고 있었다 (티켓 95)

검증하지 않았을 뿐 깨져 있던 것이 아니었다. `live_sessions`에 5건이 있었고, 그중 66초짜리
세션의 openvidu 로그가 Loki에 남아 있어 브라우저 재현 없이 판정할 수 있었다.

```
06:22:29  participant active      reporter-6     시민 접속
06:22:29  mediaTrack published ×2                영상·음성 발행
06:22:35  participant active      operator-1     관제 구독
06:23:34  participant closing     reporter-6     정상 종료
```

`Failed to ping without candidate pairs`가 대량으로 찍히지만 ICE 후보 수집 이전의 정상
노이즈다. 직후 `participant active`가 나오므로 연결에 성공한 것이다. 로그의 WARN을
장애로 읽지 않도록 주의해야 한다.

포트도 문제가 아니었다. 외부에서 열려 있는 것은 TCP 443뿐이고 7881·7880·1935는 막혀
있지만, TURN/TLS가 Caddy 443의 TCP demux를 거쳐 처리되므로(`caddy.yaml`의
`LIVEKIT_TURN_TLS_INTERNAL_PORT` 라우트) 미디어 경로가 성립한다.

## 5. Egress가 한 번도 시작되지 않은 이유 — 웹훅 미등록

`stream_recordings`가 0행이고 세션 구간에 `egress` 컨테이너 로그가 한 줄도 없다.
원인은 트리거 경로에 있었다.

```java
// LiveWebhookService.java
case PARTICIPANT_JOINED -> {
    if (!isReporter(session, event)) return;
    recordingService.startForSession(session, event.participantIdentity());
}
```

**Egress는 `PARTICIPANT_JOINED` 웹훅으로만 시작된다.** 그런데 LiveKit의 웹훅 대상은
Meet 하나뿐이다.

```yaml
webhook:
  api_key: APIEMCZ9q73NLEV
  urls:
    - http://127.0.0.1:9080/livekit/webhook     # OpenVidu Meet
```

우리 backend의 `/api/v1/live/webhooks/livekit`은 살아 있다(서명 없는 POST에 403 =
검증이 동작). 등록만 안 돼 있었다. `EGRESS_STARTED`/`EGRESS_ENDED`도 같은 경로로 오므로
녹화 생명주기 전체가 이 하나에 걸려 있다.

서명 키는 이미 일치한다(`OPENVIDU_API_KEY` = LiveKit `webhook.api_key` = `APIEMCZ9q73NLEV`).
URL만 추가하면 검증까지 통과한다.

### 5.1 등록하기 전에 경로 전체를 시뮬레이션으로 검증했다

"URL만 추가하면 된다"는 것도 가설이므로, LiveKit 설정을 바꾸기 전에 서명된 웹훅을 직접
만들어 쏴봤다. 서버에서 `OPENVIDU_API_SECRET`으로 HS256 JWT를 만들어(`iss`=api key,
`sha256`=본문 해시) `http://127.0.0.1:6080/api/v1/live/webhooks/livekit`에 POST했다.

DB를 건드리지 않도록 대상은 이미 `ENDED`인 세션으로 골랐다. `markReconnected`는
`DISCONNECTED`가 아니면 false를 반환하고 아무것도 바꾸지 않으며(`LiveSession:111`),
`startForSession`은 저장 전에 게이트웨이를 먼저 호출하므로 그 호출이 실패하면 행이
생기지 않는다.

```
정상 서명        → HTTP 204
변조 서명(대조군) → HTTP 403  COMMON_013 웹훅 서명 검증에 실패했습니다

backend  : Failed to start recording for live session 9804e9ca-…
           LiveMediaException: Egress start failed: HTTP 404
           at LiveRecordingService.startForSession(LiveRecordingService.java:75)
openvidu : API Egress.StartParticipantEgress
           {"room":"report-9804e9ca-…","identity":"reporter-6",
            "status":"404","error":"requested room does not exist"}

DB       : status=ENDED 유지, stream_recordings=0 유지
```

404는 19시간 전에 끝난 방이라 나온 의도된 결과다. 그 앞 단계가 전부 통과했다는 것이
핵심이다 — nginx 라우팅, 서명 검증, protobuf JSON 파싱과 타입 인식, `findByRoomName`,
`isReporter`, `startForSession`의 Egress API 호출까지. 예외는 잡혀서 500이 아니라 204가
나갔고 트랜잭션도 롤백되지 않았다.

대조군을 하나 더 시도했다가 무효로 판정한 것도 적어둔다. 알 수 없는 필드를 넣은 본문으로
파서가 거부하는지 보려 했으나 204가 나왔다. `WebhookReceiver`가 unknown field를 무시하기
때문이라 파서 동작의 증거로 쓸 수 없다. 타입 인식은 대신 `startForSession` 도달 자체로
증명됐다 — `UNSUPPORTED`로 파싱됐다면 그 앞에서 return 했을 것이다.

미검증으로 남은 것은 하나다. **LiveKit이 `urls` 배열의 두 대상 모두에 실제로 전송하는지**는
등록해봐야 확인된다. 문서상 배열이므로 전송하는 것이 맞지만 실측은 아니다.

## 6. OpenVidu 설정 파일은 이중 구조다 — 렌더링본을 고쳐야 적용된다

웹훅 등록을 준비하다 확인한 것이다. 컨테이너 마운트는 다음과 같다.

```
/opt/openvidu/data/runtime/config/openvidu  →  /config
```

즉 `/opt/openvidu/config/livekit.yaml`은 **읽히지 않는다.** 그것은 `${openvidu.*}` 치환 전
템플릿이고, 전체 스택 기동 시 위 런타임 경로로 렌더링된다.

- 런타임본만 고치면 즉시 적용되지만 다음 전체 재기동 때 사라진다.
- 템플릿만 고치면 지금은 무효고 다음 전체 재기동 때 적용된다.
- **둘 다 고쳐야 지금 적용되고 나중에도 유지된다.**

재기동 범위도 처음 생각보다 작다. `systemctl restart openvidu`는 유닛이
`ExecStartPre=docker-compose down`이라 Caddy까지 내려가 우리 앱도 함께 끊긴다. 반면
`docker restart openvidu`는 livekit-server 컨테이너만 재시작하므로 Caddy가 살아 있고
사이트는 유지된다. 끊기는 것은 진행 중인 라이브 세션과 Meet 방뿐이다.

이 변경은 `/opt/openvidu` 아래라 우리 CI의 rsync 대상이 아니다. 배포로 지워지지 않지만
git에도 남지 않는다. 그래서 `infra/openvidu/register-livekit-webhook.sh`에 멱등 스크립트로
남겼다. OpenVidu 업그레이드로 초기화되면 재실행하면 된다. CI는 이 스크립트를 실행하지
않는다 — OpenVidu 스택은 수동으로만 건드린다.

## 7. 다음 관문 — Egress는 우리 MinIO에 닿지 못한다

웹훅을 고치면 그 다음에 이것이 터진다. 아직 드러나지 않았을 뿐이다.

backend가 Egress에 넘기는 저장소 주소는 `live.recording.s3-endpoint`이고 그 값은
`MINIO_ENDPOINT` = `http://minio:9000`이다(`LiveKitMediaGateway.s3Upload()`).

```
egress 컨테이너            네트워크 모드 host
우리 minio                 infra_default 브리지 172.19.0.4, 호스트 포트 없음
egress → getent hosts minio    결과 없음
egress → wget http://minio:9000  exit 4 (도달 불가)
```

7/29에 MinIO의 호스트 9000 바인딩을 없애고 nginx가 9200에서 TLS를 종단하도록 바꾼 것과
맞물렸다. 해결 후보는 MinIO S3 API를 `127.0.0.1:9202` 같은 루프백 포트로 노출하고 녹화
전용 엔드포인트 변수를 따로 두는 것이다. 루프백이라 방화벽 추가 개방이 필요 없다는 점이
AWS 콘솔 권한이 없는 제약과 맞는다. `MINIO_ENDPOINT` 자체는 backend가 쓰는 값이라
그대로 둬야 하므로 `application.yml`에 변수 하나가 추가된다.

## 8. 측정에 실패한 사례 — 대조군이 없었으면 오판할 뻔했다

외부에서 UDP 443이 열렸는지 확인하려고 STUN 바인딩 요청을 보냈고 무응답이었다. 여기서
"UDP가 막혀 있다"로 넘어갔으면 WebRTC 경로를 잘못 진단했을 것이다.

대조군을 돌리자 판정이 뒤집혔다.

```
stun.l.google.com:19302   무응답
stun.cloudflare.com:3478  무응답
1.1.1.1:53 (UDP DNS)      무응답
```

**내 쪽 네트워크가 아웃바운드 UDP를 전부 막고 있었다.** 대상이 아니라 측정 지점의 문제였고,
따라서 UDP 443 결과는 무효다. 이 지점에서는 UDP를 측정할 수 없다.

같은 날 `caddy.yaml`에서 TURN 라우트를 소문자 `turn`으로만 grep해 "없다"고 판단한 일도
있었다. 실제로는 `LIVEKIT_TURN_TLS_INTERNAL_PORT`로 대문자였다. 무응답과 무결과를
부재의 증거로 쓰기 전에 측정 자체가 유효한지를 먼저 확인해야 한다.

## 9. 후속

- **웹훅 등록 실행**: 스크립트는 커밋했으나 서버 실행은 별도 판단으로 남겼다. 실행하면
  5절이 풀린다.
- **Egress → MinIO 경로**: 7절. 웹훅과 함께 처리해야 녹화가 완성된다.
- **95의 점검 절차 기록**: 위 4·5절의 조회 방법을 `LOGGING.md`에 절차로 옮긴다.
- **자원 사용량 메트릭**: Prometheus는 `openvidu` 하나만 수집한다. 부하 측정(티켓 106)에는
  cAdvisor 추가 또는 `docker stats` 수집이 필요하다. Prometheus 설정은 OpenVidu 소유다.
- **`operator-accounts.sql`**: 라이브 관제 검증에는 걸리지 않는 것으로 확인됐다.
  `requireAccess`가 `SUPER_ADMIN`을 그대로 통과시키고 `markHandling`은 배정이 없으면
  조용히 반환한다. 다만 그 경우 제보 상태가 `HANDLING`으로 바뀌지 않는다.


# 2026-07-31 (금) — data·AI 서버 배포 · nginx stale DNS 근절 · P2PNet 자산 파이프라인 검증

> 타임스탬프는 배포 태그를 포함해 대부분 UTC 원문이다. Kuma heartbeat만 KST로 조회했고
> 그 절에 표시해 뒀다.
>
> 1절은 같은 날 오전 작업이고 이후 절과 다른 세션에서 진행됐다. 그 절의 수치는 당시
> 기록을 옮긴 것이고, 2절 이후는 이 문서를 쓰는 세션에서 직접 실행해 얻은 값이다.

## 1. data 서버를 자동 배포에 넣었더니 전 API가 502가 됐다 (`a142388`)

`data`는 compose 정의(`0eef9d7`)가 있는데도 CI 배포 목록에 없어 컨테이너가 기동되지
않았다. backend는 이미 2분마다 수집을 요청하고 있어 이름 해석 실패를 반복하는 상태였다.
rsync 대상과 `docker compose up` 목록에 `data`를 넣어 해소했다.

배포는 성공했고 수집도 즉시 돌았다.

```
CITY_CONGESTION   SUCCESS 121/0
DISASTER_MESSAGE  SUCCESS 100/0
error_details 있는 run 0건
places 121행 (seoul-places.sql 수동 psql 적용)
```

그런데 **배포 직후 전 API가 502가 됐다.** 원인은 컨테이너 IP 재배치였다.

```
infra-data-1    172.19.0.6   ← backend 가 쓰던 주소
infra-backend-1 172.19.0.8   ← 밀려난 주소
```

`nginx/default.conf`는 `proxy_pass http://backend:8080`인데 `resolver` 지시자가 없다.
**nginx는 이 경우 기동 시점에 한 번만 호스트명을 해석하고 그 IP를 영구히 캐시한다.**
nginx는 7/29부터 떠 있었고 설정이 바뀌지 않아 재생성되지 않았으므로, 캐시된 `.6`을 계속
호출했다. 그 주소는 이제 data(8000 리스닝)라 8080 연결이 거부됐다.

```
[error] connect() failed (111: Connection refused) while connecting to upstream,
        upstream: "http://172.19.0.6:8080/..."
```

**`nginx -s reload`로는 풀리지 않는다.** reload는 이름을 재해석하지 않는다.
`docker restart infra-nginx-1`로 복구했다. 이 구간에 backend 자체는 무중단이었다
(`RestartCount=0`, 8090 직접 호출 200). Uptime Kuma 알림도 같은 옛 IP를 가리켰다.

이 시점에 남긴 판단은 "`ai-server`를 추가하면 똑같이 재현된다"였고, 그래서 AI 배포보다
nginx 수정을 먼저 처리했다.

## 2. nginx는 시작 시 한 번만 이름을 해석한다 — 격리 실험으로 대조했다 (`7b094f2`)

실서비스를 건드리지 않고 `infra_default`에 임시 nginx 두 개와 더미 upstream을 띄워
대조했다. 더미를 삭제·재생성해 IP를 옮긴 뒤 두 방식의 응답을 비교했다.

```
                          IP 이동 직후   15초 후
상수 proxy_pass           502            502
resolver + 변수 proxy_pass 200            200

상수 쪽 로그: upstream: "http://172.19.0.9:80/" connect refused  ← 옛 IP
```

7/31 오전 장애와 동일한 로그 패턴이 재현됐다. 수정은 두 conf 모두에 적용했다.

```
resolver 127.0.0.11 valid=10s ipv6=off;
set $backend_upstream backend;
...
proxy_pass http://$backend_upstream:8080$request_uri;
```

`proxy_pass`에 변수가 들어가야 런타임 재해석이 일어난다. 상수 호스트명으로는 안 된다.
`ipv6=off`는 내장 DNS가 AAAA를 돌려줄 때 IPv6로 먼저 붙으려다 실패하는 것을 막는다.

바꾸기 전에 전달 결과가 같은지를 확인했다. 다른 방식으로 프록시하면 OAuth2 콜백이
깨질 수 있어서다.

```
전달된 request line (상수/변수 모두 동일)
  GET /api/v1/health?code=a%2Fb&x=1
  GET /login/oauth2/code/kakao?state=%2Bx%3D
Host 헤더: i15a503.p.ssafy.io:6080   ← 포트까지 유지
```

`$request_uri`는 클라이언트가 보낸 원본 경로와 쿼리를 그대로 담으므로 퍼센트 인코딩이
재인코딩되지 않는다. 배포 후에는 실서비스에서 확인했다. backend IP를 `.6`→`.20`→`.6`으로
옮기는 동안 **nginx를 전혀 건드리지 않은 상태로 401이 45초간 끊기지 않았고**, IP 교체
구간의 nginx upstream 실패는 0건이었다. `ai-server`를 추가한 배포에서도 옛 IP 오조회는
없었다.

IP를 옮길 때 컨테이너를 재기동하지 않고 `docker network disconnect`/`connect`를 썼다.
`--force-recreate`는 IPAM이 같은 주소를 다시 주는 경우가 있어 검증이 성립하지 않았고,
재기동을 섞으면 Spring 기동 대기(약 15초 502)와 DNS 문제가 구분되지 않는다. 네트워크만
갈아끼우면 프로세스가 살아 있으므로 502가 나오면 그것은 100% 이름 해석 문제다. 재연결
시에는 `--alias backend`를 반드시 붙여야 한다.

부수 이득이 하나 있다. **변수 방식은 기동 시 이름을 해석하지 않으므로 upstream이 아직
없어도 nginx가 뜬다.** 전에는 해석 실패가 기동 자체를 막았다(5절).

## 3. conf를 파일 단위로 마운트하면 배포가 반영되지 않는다 (같은 커밋)

수정을 커밋하기 전에 "이게 배포로 적용되는가"를 확인했는데, 답이 아니었다.

CI 배포의 rsync는 임시 파일에 받아 rename으로 교체하므로 **파일의 inode가 바뀐다.**
파일 단위 바인드 마운트는 마운트 시점의 inode에 묶여 있어 컨테이너 안에서는 옛 내용이
계속 보인다.

```
마운트 방식                          rsync 교체 후 컨테이너가 보는 내용
./nginx/default.conf:...(파일)       ORIGINAL   ← 반영 안 됨
./nginx:/etc/nginx/conf.d(디렉터리)  REPLACED
```

여기에 compose는 nginx 정의가 바뀌지 않으면 컨테이너를 재생성하지 않는다는 점이 겹친다.
**즉 지금까지는 conf만 고쳐 배포하면 적용될 방법이 없었고, 컨테이너를 재생성해야만
반영됐다.** 그동안 반영된 것은 다른 이유로 nginx가 재생성됐기 때문이다.

디렉터리 마운트로 바꾸고, CI 배포 뒤에 reload를 넣었다.

```
docker compose exec -T nginx nginx -t &&
docker compose exec -T nginx nginx -s reload
```

reload는 새 워커를 띄우고 기존 연결을 유지해 무중단이다. 서버에서 같은 명령을 실행해
워커 교체(PID 20→325, 옛 워커는 graceful shutdown)와 6080 401 / 9200 200 유지를
확인했다. `nginx -t`를 먼저 두어 잘못된 conf를 reload하지 않게 했다. 문법 오류면 배포가
실패로 끝나는데, 그게 옛 설정으로 조용히 계속 도는 것보다 낫다.

`docker-compose.yml`이 바뀌므로 이 배포에서 nginx가 자동 재생성되어 새 conf가 적용됐다.

## 4. 그 실험을 한 번 오판했다 — rsync가 전송을 건너뛰었다

3절의 첫 실험에서 디렉터리 마운트도 옛 내용을 보여 "둘 다 반영되지 않는다"고 결론냈다.
틀렸다. 원본과 교체본이 **같은 크기(9바이트)에 같은 초에 만들어져 rsync가 전송을
건너뛴** 것이었다. 컨테이너는 바뀐 적이 없는 파일을 보고 있었을 뿐이다.

크기와 mtime을 분리하고 `--itemize-changes`로 전송을 확인한 뒤 결론이 뒤집혔다.

```
>f.st...... t.conf     ← 두 경우 모두 실제 전송됨
파일 마운트   : ORIGINAL-AAA
디렉터리 마운트: REPLACED-BBBBBBBBBB
```

**대조 실험에서 "변화가 없다"는 결과는, 변화를 실제로 만들었는지부터 증명해야 한다.**
7/28 4절과 7/30 8절에 같은 성격의 오진이 있다. 세 번째다.

## 5. minio.conf의 상수 호스트명은 nginx 전체를 못 뜨게 만든다 (`6899a46`)

`minio.conf`는 처음에 손대지 않기로 했다. minio는 이미지 태그가 고정이라 재생성이 드물어
stale DNS 위험이 낮다고 봤기 때문이다. 그 판단을 뒤집은 것은 `nginx -t` 결과였다.

```
nginx: [emerg] host not found in upstream "minio" in minio.conf:52
nginx: configuration file test failed
```

**상수 호스트명은 기동 시 해석에 실패하면 nginx 전체가 뜨지 않는다.** stale IP보다 나쁘다.
minio가 재생성되는 중에 nginx가 기동하면 MinIO뿐 아니라 앱 전체가 죽는 연쇄가 된다.
변수 방식은 기동 시 해석하지 않으므로 이 연쇄가 끊긴다.

이 파일은 presigned URL을 프록시하므로 서명 호환이 깨지면 업로드가 전멸한다. 그래서
실제 presigned URL을 발급해 확인했다.

```
mc share download 로 발급 (X-Amz-Algorithm/Credential/Date/Expires/SignedHeaders/Signature)
상수 proxy_pass (실서비스 9200) : 200
변수 proxy_pass (임시 19443)    : 200, 본문 일치
```

`Share:` 줄의 서명된 URL을 써야 한다. 처음에 출력 첫 줄(`URL:`, 서명 없음)을 잡아 양쪽
403을 받았고, 그대로 "대조 성립"으로 넘겼으면 서명 검증을 안 한 채 넘어갔을 뻔했다.

## 6. P2PNet 가중치 인증 — presigned를 버렸다 (7일 상한 실측)

137번은 가중치를 MinIO로 옮기는 티켓이고, AI 쪽 문서는 presigned URL을 권장으로
기술하고 있었다. 실측이 그 권장을 뒤집었다.

```
mc share download --expire 169h    → Expiry cannot be larger than 7 days
mc share download --expire 604801s → 동일
```

**상한이 7일이다.** 오늘 발급분은 8/7에 만료되고, 8/9 리허설과 8/10 제출 사이에 재발급
+`.env` 수정+`ai-server` 재시작이 필요해진다. 8/8 프리즈 이후에 손을 대야 한다는 뜻이다.

여기서 중요한 것은 어떤 방식이든 **실패가 조용하다**는 공통 성질이다. 자산 준비가
실패하면 코드가 P2PNet만 끄고 YOLO로 계속 서비스한다. API는 200을 주고 알림도 없다.
그래서 "언제 조용히 깨지는가"가 선택 기준이 됐다.

| 방식 | 만료 | 공유비밀 | 외부노출 | 자동복구 | 비용 |
|---|---|---|---|---|---|
| presigned | **8/7 (7일 상한)** | 없음 | URL 유출 시 7일 | O | `.env` 1줄 |
| nginx 헤더 검증 | 없음 | 있음 | 낮음 | O | 비밀을 Git 커밋되는 conf에 못 씀 → envsubst 구조 도입 필요 |
| **내부 익명 + 외부 차단** | 없음 | 없음 | nginx 규칙 하나에 의존 | O | conf 1개 + `.env` 1줄 |
| 볼륨 직접 주입 | 없음 | 없음 | 없음 | **X (수동)** | URL 비움 |

세 번째를 택했다. presigned의 만료는 오늘 검증해도 시간이 흐르면 다시 깨지는데, 이쪽의
약점(nginx 규칙 의존)은 규칙이 Git에 있어 rsync로 유지되고 `curl` 한 줄로 언제든
재검증된다. 만료도 공유비밀도 없다.

```
버킷    safy-ai-models (p2pnet prefix 에만 mc anonymous set download)
내부    ai-server → http://minio:9000/safy-ai-models/p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth
외부    nginx/minio.conf 에 location ^~ /safy-ai-models { return 404; }
```

minio 컨테이너 포트는 `127.0.0.1`에만 게시되므로 외부 경로는 nginx 9200뿐이다.
**즉 이 nginx 규칙이 가중치를 인터넷에서 떼어놓는 유일한 경계다.** 지우면 공개된다.
403이 아니라 404로 답해 버킷 존재 여부도 알리지 않는다.

대가 하나는 브리지 안의 모든 컨테이너가 인증 없이 이 prefix를 읽는다는 것이다. 같은
네트워크의 backend는 이미 MinIO 자격증명을 갖고 있어 실질적 권한 확대는 미미하다고
판단했다(**추론**).

## 7. AI 서버를 가중치 없이 먼저 배포했다 (`6899a46`)

가중치 파일은 저장소에 없고 AI 담당자 로컬에만 있다. 파일을 기다리지 않고 배포한 근거는
세 가지다.

- YOLO 가중치는 이미지 빌드 단계에서 내려받아 SHA-256까지 검증해 넣는다(`ai/Dockerfile`)
- `P2PNET_SHADOW_ENABLED=false`면 자산 준비를 건너뛰고 `ASSET_DISABLED`로 끝난다
- `/health`는 P2PNet 상태와 무관하게 200을 준다

서버 `.env`에 P2PNET 키가 하나도 없어 compose 기본값 `false`가 그대로 적용된다. 가중치가
오면 `.env` 두 줄과 재시작만 남는다.

CI에 넣기 전에 서버에서 이미지를 수동 빌드해 기동·응답·자원을 먼저 확인하고 임시
컨테이너·이미지를 삭제했다. 빌드 실패가 배포 파이프라인을 막는 것을 미리 걸러내기
위해서다. 실제로 1차 빌드가 실패했다(12절).

`ai-server` 빌드 실패가 `docker compose up` 체인을 끊어 배포 전체를 실패시키는 구조는
그대로 뒀다. 실패를 무시하도록 분리하면 옛 컨테이너가 계속 도는데도 배포가 성공한 것으로
보인다. 그게 더 나쁘다.

## 8. AI 서버 실측 (티켓 106 입력값)

배포된 컨테이너에서 측정했다. 프레임은 AI 담당자가 보낸 검증용 합성 이미지와 컨테이너
내장 Ultralytics 샘플을 썼다.

```
최초 빌드(캐시 없음)  213.47 s
이미지 크기            2.85 GB
기동 ~ /health 200     5.19 s
첫 요청                processingTimeMs 2151   ← 모델 로드
RSS                    유휴 202MiB → 추론 후 410.5MiB
```

| 프레임 | 왕복 mean / p95 / max | `processingTimeMs` mean / p95 |
|---|---|---|
| 1280×720 합성 (n=30) | 83.9 / 86.4 / 89.3 ms | 60.5 / 63.0 ms |
| `bus.jpg` 사람 4명 (n=30) | 95.0 / 107.0 / 107.2 ms | 71.7 / 84.0 ms |
| 3840×2160 (n=20) | 173.6 / 176.4 / 179.7 ms | 61.0 / 63.0 ms |

읽을 지점이 두 개다.

- **사람이 탐지되면 추론이 약 11ms 늘어난다**(60.5 → 71.7). 사람 0명 프레임으로만 측정하면
  후처리 비용을 놓친다.
- **4K는 추론 시간이 720p와 같다**(61ms). YOLO가 내부에서 리사이즈하기 때문이다. 왕복
  차이 112ms는 1.3MB 업로드와 디코드다. 큰 프레임에서는 네트워크·디코드가 지배적이다.

동시 4요청은 직렬로 처리된다(uvicorn 단일 워커). 총 470ms, 개별 0.12/0.23/0.34/0.45초.
Egress 프레임 추출 간격이 5초이므로 왕복 p95 86.4ms는 예산의 약 1.7%다. 노트북 P95
68.81ms 대비 1.26배다. **YOLO 기준으로 CPU 단독 배포는 충분하다.**

미확보 두 가지를 분명히 남긴다. **P2PNet은 가중치가 없어 미측정이고**, 노트북 값에 위
1.26배를 곱하는 것은 비율이 같다는 가정에 기댄 추정이라 실측으로 대체해야 한다.
**Egress 트랜스코딩과 겹쳤을 때의 메모리도 미측정이다** — 라이브 세션이 없으면 Egress가
돌지 않으므로 티켓 95의 브라우저 라이브 제보 검증 때 같이 재야 한다.

## 9. P2PNet 자산 실패 3케이스를 실제 서버에서 격리 검증했다 (티켓 141)

가중치가 없어도 격리는 검증할 수 있다. 82.39MB 더미 파일을 실제 경로에 올려 체크섬
불일치를 만들고, 임시 컨테이너로 세 실패를 재현했다. 운영 `ai-server`는 건드리지 않았다.

| 조건 | `p2pnetShadow.status` | `/health` | YOLO 공식 분석 |
|---|---|---|---|
| URL 없음 | `P2PNET_WEIGHT_URL_MISSING` | ok 200 | `COMPLETED` personCount 4 C 1.88 |
| 없는 URL | `P2PNET_WEIGHT_DOWNLOAD_FAILED` | ok 200 | `COMPLETED` personCount 4 C 1.88 |
| 체크섬 불일치 | `P2PNET_WEIGHT_CHECKSUM_MISMATCH` | ok 200 | `COMPLETED` personCount 4 C 1.88 |

세 경우 모두 P2PNet만 꺼지고 공식 경로는 동일한 결과를 냈다. `congestionSource`도 계속
`YOLO`다. 부수로 세 케이스 모두 `modelVersion`이 `safy-p2pnet-v2-epoch3`로 채워졌는데,
이는 **manifest 로드와 `/opt/p2pnet` pinned commit 검증이 통과했다는 뜻**이다. 소스가
어긋나면 `SOURCE_MISSING`/`VERSION_MISMATCH`에서 먼저 걸린다.

같은 기회에 MinIO 배선을 선행 검증했다.

```
p2pnet prefix 익명 정책 부여 후
  내부 minio:9000 익명 GET  200, 86,390,702 bytes, 0.08 s
  외부 9200                 404
  /safy-reports/            403 (회귀 없음)
```

**내부 다운로드가 0.08초라 기동 지연 요인이 아니다.** 검증 후 더미 파일은 삭제하고 익명
정책만 남겼다. 잘못된 파일이 실제 가중치 경로에 남으면 혼란을 준다. 실제 가중치가 오면
파일만 올리면 `READY`가 된다.

## 10. 앱 로그가 Loki에 한 줄도 없다 (티켓 142 차단, AI 영역)

AI 담당자가 "앱에 로깅 설정이 없어 `logger.info`가 안 나올 것"이라는 가설을 보냈다.
확인 결과 맞았고, 더 중요하게 **원인 지점이 갈렸다.**

```
{container="infra-ai-server-1"}                          → uvicorn 로그 조회됨 (수집 정상)
{container="infra-ai-server-1"} |= "p2pnet_asset_startup" → 0건
{job=~".+"} |= "p2pnet"                                   → 0건
docker logs infra-ai-server-1 | grep p2pnet_asset_startup → 0건 (uvicorn 로그 11줄만)
```

`logging=true` 라벨과 Alloy 수집, Loki 저장은 모두 정상이다. 앱의 `logger.info`가 stdout에
도달하지 못한다. root 로거에 핸들러가 없어 `logging.lastResort`(WARNING)가 INFO를 버린다.
**인프라가 고칠 것은 없고 AI 쪽 로깅 설정 문제다.**

`ENABLED=false`여도 `_log_preparation`은 호출되므로, 수정되면 지금 상태에서도
`status=DISABLED` 한 줄이 남아야 한다. 그게 수정 확인 기준이다.

## 11. Uptime Kuma는 docker sandbox 레벨에서 stale DNS를 겪는다 (미해결)

nginx를 고친 뒤에도 Kuma에서 backend DOWN 알림이 왔다. 오탐이었다.

```
heartbeat (KST)
  15:11:12  status=1
  15:12:12  status=2  connect ECONNREFUSED 172.19.0.6:8080   ← backend 는 .7 로 이동한 뒤
  15:15:12  status=0  → 알림 발송
  15:21:12  status=1  자연 회복
```

그 9분간 실제 backend는 무중단이었다(`restarts=0`, 8090 직접 200, 수집 SUCCESS 유지).
**같은 시각 nginx는 이미 `.7`로 정확히 붙고 있었다.** 같은 docker DNS인데 컨테이너별로
다른 답을 받은 것이다.

Kuma 안에서 `getent hosts`와 `node dns.resolve4` 둘 다 stale 값을 줬으므로 앱
캐시(`cacheable-lookup`)가 아니고, `/etc/hosts`에도 항목이 없다(docker 29.6.2). docker
network inspect에도 유령 엔드포인트가 없었다. sandbox 레벨 문제로 본다.

같은 날 두 번째 사례에서는 3분(`status=2` 3회)만 지속되고 회복해 **알림까지 가지
않았다.** 즉 지속 시간이 가변적이고, **알림이 없다는 것이 오탐이 없었다는 뜻은 아니다.**

구별법은 단순하다. heartbeat `msg`의 IP가 현재 컨테이너 IP와 다르면 오탐이다.

대책 후보는 세 가지다. compose에 `extra_hosts: host.docker.internal:host-gateway`를 주고
모니터를 게이트웨이 경유로 바꾸면 DNS를 아예 타지 않는다(권장, 다만 모니터 설정이 Kuma UI
소유라 코드 밖 작업이 남는다). 컨테이너 고정 IP는 근본적이지만 전 서비스 IP를 수동
관리해야 한다. 배포 후 Kuma 재시작은 가장 단순하지만 sandbox 캐시가 초기화되는지
**미확인**이다.

**backend → `http://data:8000` 경로도 같은 병에 노출돼 있다.** JVM은 DNS를 캐시한다.
7/31 배포에서는 backend도 함께 재생성돼 무사했지만, data만 IP가 바뀌는 배포 조합은
위험하다(미발생, **추론**).

## 12. 별건 관찰

- **`ai/deploy/p2pnet-compatibility.patch`가 Windows 체크아웃에서 CRLF가 된다.** 1차 빌드가
  `git apply --check` 거부로 실패한 원인이다. blob은 LF 514B, 워킹트리는 CRLF 529B이고
  `.gitattributes`는 `*.sh`만 `eol=lf`로 고정한다. `/opt/p2pnet`은 컨테이너에서 새로
  clone되므로 대상은 항상 LF다. **CI(리눅스 러너)와 서버 빌드는 영향이 없다** — 서버에서
  `git -c core.autocrlf=false archive`로 LF를 보내 빌드 성공까지 확인했다. Windows 로컬
  빌드에만 걸린다. AI 담당자가 원인을 바이트 단위로 재확인하고 `*.patch text eol=lf`
  적용까지 마쳤으며, 루트 공유 파일이라 다음 AI MR에 묶어 가기로 했다.
- **검증 스크립트 `measure-latency.sh`의 awk 블록이 개행 손상으로 실행되지 않는다**
  (`unterminated string`). zip 생성 과정에서 `printf "...%.1f ms\n"`의 `\n`이 실제 개행으로
  바뀐 것으로 보인다. sh는 문자열 안 개행을 허용해 `run-all.sh`는 정상 동작한다. 측정
  루프는 정상이라 8절 수치는 동등한 방식으로 다시 계산했다.
- **재난문자 수집량이 하루 안에 100 → 1000 → 374로 두 번 바뀌었다.** 151번(수집 한도)과
  `22d0f34`(요청 포맷 일치)가 차례로 배포된 결과다. 374는 하루 발생 규모와 비슷해
  정상화로 보이지만(**추론**), 숫자의 의미와 기존 오적재분 정리는 데이터 담당 확인
  사항이다.
- AI 담당자가 보낸 검증 이미지 9개는 기대 결과와 전부 일치했다. `07-large-4k.jpg`가
  `COMPLETED`로 나와 154번(4K 오판 수정)이 운영에 반영된 것도 확인됐다
  (`frameQualityRuleVersion=frame-quality-v0.3`).

## 13. 오늘 배운 점

- **`resolver` 없는 `proxy_pass`는 시한폭탄이다.** 컨테이너가 하나 늘어날 때마다 IP가
  재배치되고, nginx는 설정이 안 바뀌면 재생성되지 않아 옛 주소를 계속 부른다. 상수
  호스트명은 그 위에 "기동 시 해석 실패 = 전체 기동 실패"라는 두 번째 위험을 얹는다.
- **파일 단위 바인드 마운트는 rsync 배포와 함께 쓰면 침묵한다.** 인증서에 대해 이미 알고
  있던 함정(7/29 4절)이 conf 파일 자체에도 그대로 적용됐는데 지금까지 몰랐다. 같은 원리가
  적용되는 곳을 한 번 훑을 가치가 있다.
- **"아무 일도 일어나지 않았다"는 관측은 관측이 유효했는지부터 증명해야 한다.** rsync
  스킵으로 결론을 뒤집은 것이 오늘이고, 7/28·7/30에도 같은 성격의 오진이 있었다.
- **조용한 실패를 기준으로 설계를 고르는 것이 유효했다.** presigned를 버린 이유는 기술적
  우열이 아니라 "언제 아무 신호 없이 깨지는가"였다. 만료 기한은 캘린더에만 존재하고
  런타임에는 아무 경고도 남기지 않는다.
- **감시 도구도 같은 병에 걸린다.** nginx를 고쳤다고 stale DNS 문제가 끝난 것이 아니었다.
  Kuma는 여전히 오탐을 내고, 그 오탐이 알림까지 갈지는 지속 시간에 달려 있다.

## 14. 후속 · 미결

- **P2PNet 가중치 수신·게시**: 파일이 저장소에 없어 인프라 단독으로 끝낼 수 없다. scp로
  받아 전송 전/후/업로드 후 3중 체크섬을 대조한 뒤 `mc cp`한다. MinIO 자격증명은 공유하지
  않는다. 배선과 격리는 이미 검증돼 있어 파일만 올리면 `READY`가 된다.
- **P2PNet 지연·RSS 실측과 swap 판단**: 가중치 활성화 후. 현재 최대 RSS 410.5MiB,
  메모리 여유 11G, swap 0B다. P2PNet 로드 후 값이 나와야 swap 추가 여부를 정할 수 있다.
- **Egress 겹침 메모리**: 티켓 95 브라우저 라이브 제보 검증과 같이 측정한다.
- **Kuma stale DNS 대책**: 11절 A안(게이트웨이 경유)을 권한다. 모니터 설정 변경이 Kuma UI
  소유라 코드 변경만으로는 끝나지 않는다.
- **Kuma `ai-server` 모니터 활성화**: `active=0`으로 이미 등록돼 있다. 자산 실패는 200을
  주며 조용히 넘어가므로 감시가 필요하다. 가중치 활성화 후에는 Keyword 모니터로
  `"ready":true`를 검사하면 P2PNet 상태까지 볼 수 있다.
- **MinIO 자격증명 교체**: 9200이 외부에 열려 있는 것과 함께 검토해야 한다. 교체하면
  backend·egress 재시작이 필요하다. 상세는 문서에 남기지 않고 별도 채널로 공유한다.
- **backend → data 이름 해석**: 11절 마지막 항목. data만 IP가 바뀌는 배포 조합에서
  재현될 수 있다.
- **프레임 전달 주체 미정**: `ai-server`는 떠 있지만 아무도 프레임을 보내지 않는다.
  `ai/docs/backend-integration-contract.md` 7.1에도 미결로 적혀 있다. 백엔드·OpenVidu와
  결정해야 하며, 정해지지 않으면 AI가 배포돼도 실제 동작을 볼 수 없다.
- **앱 로깅 설정(티켓 142 전제)**: 10절. AI 영역이고 수정 확인 기준은 `status=DISABLED`
  한 줄이 Loki에 나타나는 것이다.

---

# 2026-08-03 (일) ~ 08-04 (월) — 주말 배포 검증 · AI 부하 실측 · GPU 노트북 터널 전환

이틀 동안 세 가지가 연달아 일어났다. 주말에 팀원들이 올린 AI 연동 코드의 배선 누락을
찾았고, 티켓 106 부하 측정으로 서버 CPU에서는 P2PNet 보정이 불가능하다는 결론을 냈고,
그 결론 때문에 AI 서버가 GPU 노트북으로 옮겨가면서 터널 경로를 새로 만들었다.

## 1. 주말 배포가 실제로 반영됐는지 전 계층 대조

"파이프라인이 통과했다"와 "내 변경이 서비스에 반영됐다"는 다른 문제다. 다섯 계층을
따로 확인했다.

| 계층 | 확인 방법 | 결과 |
|---|---|---|
| develop HEAD | `git log origin/develop` | `8eefcb8` |
| 서버 이미지 태그 | `docker ps` | `safy-backend/ai/data:8eefcb80` — 일치 |
| 컨테이너 재생성 | `docker inspect .State.StartedAt` | 배포 시각과 일치 |
| 프론트 정적 파일 | 서버 `dist` mtime vs 마지막 커밋 시각 | 일치 |
| 라이브 서빙 | `curl`로 번들 파일명·바이트 크기 대조 | 서버 파일과 동일 |

프론트에서 한 번 헷갈렸다. 서버 mtime은 UTC이고 `git log`는 KST라 9시간 차이가 난다.
`frontend-user/dist`가 `07-31 04:05` UTC인데 마지막 커밋이 `07-31 13:05` KST여서 처음엔
"커밋이 dist보다 나중"으로 보였다. 같은 시각이다.

`frontend-user`는 7/31 이후 커밋이 0건이라 갱신되지 않은 것이 정상이다. CI의 프론트 잡은
`changes: frontend-user/**/*` 조건부이고, 아티팩트가 없으면 deploy가 rsync를 건너뛴다.

DB 스키마도 따라 올라왔다. `media_processing_jobs`, `media_analysis_results`,
`media_blur_results`, `risk_assessments`, `grid_cells`(6,782행),
`place_grid_mappings`(7,346행)가 모두 존재했다. 마이그레이션은 없다 — 운영이 `dev`
프로필로 돌아 `ddl-auto: update`가 자동 생성했다(아래 7절).

`stream_recordings`가 **0 → 5행**이 됐다. 티켓 95의 Egress 녹화가 주말에 실제로 돌았다.

## 2. backend가 AI를 부를 주소가 없었다 — 같은 병 두 번째

주말 커밋 `6aeffb8`이 `AiAnalysisClient`와 `MediaProcessingWorker`를 넣었다. 이것으로
**프레임 전달 주체가 정해졌다** — 백엔드 내부 워커가 `media_processing_jobs`를 2초 주기로
폴링한다(`@Scheduled(fixedDelayString = "${ai.processing.scheduler-delay:PT2S}")`).
7/31에 미결로 남겼던 항목이 해소됐다.

그런데 주소가 틀렸다.

```
application.yml:129   base-url: ${AI_SERVER_BASE_URL:http://localhost:8000}
```

- `docker exec infra-backend-1 printenv` → `AI_SERVER_BASE_URL` 없음
- `docker-compose.yml` backend 블록에 없음
- `application.yml` / `-dev.yml` / `-prod.yml` 어디에도 override 없음
- 따라서 기본값 `http://localhost:8000`이 적용된다. 이건 backend 컨테이너 **자신**이고,
  backend는 컨테이너 안에서 **8080만 LISTEN**한다(`docker port` = `8080/tcp ->
  127.0.0.1:8090`). 8000에는 아무것도 없다
- 반면 `ai-server:8000`은 같은 네트워크에서 정상 도달한다(일회용 alpine에서 `/health` 200)

**7/31에 `PUBLIC_DATA_BASE_URL`에서 겪은 것과 완전히 같은 병이다.** compose 주석에 그
경위를 적어뒀는데도 새 외부 호출이 추가될 때 다시 발생했다.

아직 터지지 않은 이유는 `media_processing_jobs`가 0행이라서다. 워커는 2초마다 돌지만
처리할 job이 없어 조용하다. backend 로그 6시간치가 56줄뿐인 것도 그래서다.
**실제 제보가 한 건 들어오는 순간부터 전건 실패한다.**

`backend/.env.example`에는 `AI_SERVER_BASE_URL=http://localhost:8000`이 적혀 있다.
컨테이너 밖에서 직접 실행할 때의 값으로는 맞다. 컨테이너용 값을 채우는 것은
`infra/docker-compose.yml` 소유이므로 인프라가 고쳤다.

**교훈**: BE가 새 외부 호출을 추가하면 compose env 배선을 같이 확인해야 한다. 코드 리뷰만
으로는 드러나지 않는다 — `application.yml`의 기본값이 그럴듯해 보이기 때문이다.

## 3. compose는 `env_file`을 쓰지 않는다 — `.env`만 고치는 우회가 불가능한 이유

"git을 건드리지 않고 서버 `.env`만 고치면 되지 않나"를 확인했더니 안 됐다.

```
grep env_file docker-compose.yml  →  (사용 없음)
```

`env_file`이 없으므로 `.env`의 값은 `environment:` 목록에 `${...}`로 **선언된 키만**
컨테이너에 들어간다. 선언 자체가 없으면 `.env`에 아무리 적어도 전달되지 않는다.

`docker-compose.yml.override` 를 서버에 두는 우회도 `rsync --delete`가 지운다
(`--exclude=.env`는 `.env`만 보호한다).

그래서 compose 수정이 유일한 방법이고, 그건 인프라 소유 파일이다. 충돌 위험도 git 이력으로
확인했다 — `docker-compose.yml` 수정 18건 중 16건이 인프라, 2건이 AI 담당자(ai-server 블록)
이고 BE·FE 담당자는 한 번도 손대지 않았다.

## 4. P2PNet 서버 CPU 부하 측정 (티켓 106)

AI 담당자가 요청한 매트릭스를 임시 컨테이너로 측정했다. 운영 `ai-server`는 건드리지 않고
같은 이미지(`safy-ai:8eefcb80`)로 별도 컨테이너를 띄웠다.

### 핵심 상수

```
YOLO 추론      63~88ms
P2PNet 추론    4,580ms   (중앙값, n=14, 범위 4,330~4,684ms)
```

P2PNet 추론이 사실상 상수다. 코드상 입력은 긴 변을 1536으로 축소한 뒤 128의 배수로
내림되므로(`p2pnet_benchmark_adapter.py`, `MAX_INPUT_SIDE=1536`, `MULTIPLE=128`), 운영
프레임 1280×720은 항상 1280×640으로 들어간다(`LIVE_FRAME_WIDTH=1280`).

실측으로도 픽셀 수가 지배했다.

| 입력 | P2PNet 실제 입력 | 픽셀 수 | 추론시간 |
|---|---|---|---|
| 1280×720 합성 텍스처 | 1280×640 | 819,200 | 4,548ms |
| 1280×720 실사(bus.jpg, 4명) | 1280×640 | 819,200 | 4,472ms |
| 3840×2160 합성 | 1536×768 | 1,179,648 | 6,690ms |

픽셀 수 1.44배에 시간 1.47배. 오차 2%다.

**사람 수는 지연에 무관하다.** 같은 프레임에서 `P2PNET_SHADOW_THRESHOLD`를 0.5 → 0.02로
내려 `personCount`를 2 → **278(139배)**로 만들었는데 추론시간은 4,471 → 4,608ms(**+3%**)
였다. P2PNet은 고정된 proposal 격자 전체를 예측하고 threshold는 그 뒤에 고르는 필터라
연산량이 인원수와 무관하다. 이 실험 덕분에 **고밀도 실사 프레임을 구할 필요가 없어졌다**
— 뉴스 영상이나 생성형 영상을 프레임으로 끊는 작업을 안 해도 됐다.

### 매트릭스

YOLO 단독(5초 주기, 왕복 ms):

| 동시 영상 | n | 평균 | p95 | 최대 | 추론 평균 |
|---|---|---|---|---|---|
| 1 | 30 | 96.5 | 105.3 | 125.1 | 67.9 |
| 2 | 40 | 100.8 | 122.0 | 203.6 | 69.5 |
| 3 | 60 | 106.8 | 142.3 | 291.4 | 71.2 |
| 5 | 100 | 110.0 | 159.8 | 483.1 | 70.2 |

CPU 최대 117%, 프로세스 RSS 최대 460.9MiB(컨테이너 335.2MiB), 서버 가용 최소 11,892MB.

YOLO+P2PNet(보정 OFF, 타임아웃 5초):

| 동시 영상 | n | 평균 | p95 | 최대 | shadow 타임아웃 |
|---|---|---|---|---|---|
| 1 | 30 | 112.0 | 143.4 | 150.1 | **0/30** |
| 2 | 40 | 203.7 | 330.6 | 337.5 | 39/40 |
| 3 | 30 | 204.4 | 349.7 | 455.4 | 29/30 |
| 5 | 50 | 232.4 | 488.5 | 975.0 | 50/50 |

CPU 최대 **331.65%**, 프로세스 RSS 최대 **2,170.6MiB**(컨테이너 2.016GiB), 서버 가용 최소
9,326MB. 두 컨테이너 모두 `RestartCount=0`, `OOMKilled=false`.

**YOLO 공식 응답은 동시 영상 5개에서도 p95 488ms로 5초 예산의 10%다.** shadow가 전멸하는
동안에도 공식 경로는 유지됐다. 격리가 숫자로 확인됐다.

### 락 대기시간

앱이 계측하지 않아 타임아웃을 120초로 열어 아무것도 잘리지 않게 한 뒤
`왕복 − YOLO 추론 − P2PNet 추론 − 오버헤드(57ms)`로 산출했다.

| 동시 영상 | 왕복 평균 | 락 대기 평균 | 락 대기 최대 |
|---|---|---|---|
| 1 | 4,754ms | 141ms | 228ms |
| 2 | 5,154ms | 537ms | 4,806ms |
| 3 | 8,946ms | 4,272ms | 9,345ms |

### 타임아웃이 스레드를 죽이지 못한다

`shadow_analysis.py`의 구조가 문제였다.

```python
shadow = await asyncio.wait_for(
    asyncio.to_thread(self._run_shadow, image_bytes),
    timeout=self.config.timeout_seconds)     # 5.0
...
def _run_shadow(self, image_bytes):
    with self._inference_lock:               # 락 획득이 스레드 '안'
        result = self._get_runner()(image_bytes)
```

세 가지가 겹친다.

1. **락 대기가 5초 예산에 포함된다.** 락이 `to_thread` 안에 있어 앞 요청이 추론 중이면
   뒤 요청은 대기부터 예산을 깎는다
2. **`wait_for` 타임아웃은 스레드를 취소하지 못한다.** 취소되는 건 await뿐이고 추론
   스레드는 계속 돌며 락을 계속 쥔다. 한 번 겹치면 연쇄로 전멸한다
3. **첫 추론에 모델 로드가 락 안에서 포함된다.** 워밍업 요청은 거의 확실히 타임아웃된다

부수 결과 하나: 타임아웃 값을 올려도 **CPU 비용이 같다**. 타임아웃은 결과만 버리고
스레드는 계속 돌기 때문이다. 5초 → 30초로 올렸을 때 동시 영상 2개에서 shadow 성공이
1/16 → **14/17**로 올랐다. 버려지던 계산을 기록으로 살린 것이다.

### 결론

**보정 활성화는 서버 CPU에서 불가능하다.** 유휴 서버에 단독 요청 1건만 보내도 왕복
4,754ms로 5초 예산의 95%다. 판단 기준 "p95 4초 이하"를 동시 영상 1개에서도 못 맞춘다.

**그림자 분석만 켜는 것(보정 OFF)은 동시 영상 1개까지 성립한다**(타임아웃 0/30).
2개부터 무너진다. 처리량으로도 설명된다 — 직렬 추론 4.58초면 상한이 0.218장/초이고,
영상 1개가 0.2장/초를 요구해 이용률 92%다. 2개면 183%다.

## 5. 부하 생성기가 닫힌 루프였다 — 측정 함정

락 대기를 측정할 때 부하 생성기가 `curl` **응답을 기다린 뒤** 5초를 쉬는 구조였다. 보정
ON은 응답이 P2PNet을 기다리므로 다음 프레임 전송이 밀려 **클라이언트가 스스로 유량을
조절한다.** 그래서 동시 영상 2개가 안정적으로 보였다.

실제 Egress는 응답과 무관하게 5초마다 프레임을 만드는 **열린 루프**다. 그대로 다시 쟀다.

```
보정 ON, 동시 영상 2개, 응답 대기 없이 5초마다 전송
r1  9,205 / 4,671ms
r2 13,432 / 8,941
r3 13,262 / 17,909
r4 22,169 / 17,567
r5 26,409 / 21,693
r6 30,769 / 26,012
r7 35,060 / 30,461
r8 34,815 / 39,378ms
```

**단조 증가.** 라운드당 약 4.2초씩 쌓인다(영상 2개 × 4.58초 − 5초 간격). 대기열 무한
증가가 추론이 아니라 실측이 됐다.

보정 OFF는 응답이 150ms라 사실상 열린 루프여서 그 수치는 유효하다.

**규칙**: 측정 조건에 느린 동기 경로가 포함되면 harness가 열린 루프인지 닫힌 루프인지
명시해야 한다. 닫힌 루프는 시스템이 아니라 클라이언트를 측정한다.

## 6. CI 러너가 같은 4 vCPU를 쓴다

측정 재현 중에 P2PNet 추론이 4,791~10,856ms로 흔들렸다. 원인은 GitLab 러너였다.

```
러너 빌드 컨테이너   CPU 306.52%   (java 289% + 108%, Gradle)
load average        3.17
```

같은 조건을 3회 시도해 시도별로 shadow 기록을 분리했다.

| 시도 | 러너 | load 최대 | shadow 실패 | 성공분 추론 |
|---|---|---|---|---|
| 1 | 있음 | 4.34 | **9/30** | 4,732ms |
| 2 | 있음 | 5.68 | **23/30** | 4,615ms |
| 3 | **없음** | 3.24 | **0/30** | 4,524ms |

**CI 부하와 실패율이 단조 관계다.** 성공분 추론시간이 비슷해 보이는 것은 5초를 넘은 것이
FAILED로 빠진 검열 때문이다.

### 측정 전후로 러너 부재를 확인하는 방법

이 러너는 **종료 컨테이너를 자동 삭제하지 않는다**(`-predefined`가 `Exited`로 남는다).
그래서 호스트에 남은 러너 컨테이너의 `CreatedAt`으로 **과거 측정창에 잡이 있었는지 사후
검증할 수 있다.**

```bash
docker ps -a --filter name=runner --format '{{.CreatedAt}} | {{.Names}} | {{.Status}}'
cat /proc/loadavg
docker system df    # Build Cache·이미지 수 불변이면 빌드가 없었다
```

오전 측정이 유효했던 근거가 이것이다 — 그 측정창에 러너 컨테이너가 없었고, Build Cache와
이미지 수가 불변이었고, 편차가 8%로 일관적이었다. 오염된 데이터는 편차 126%로 흩어진다.

반대 방향의 배려도 필요하다. 팀원 CI가 도는 중에 부하 프로브를 띄우면 **그쪽 파이프라인을
느리게 만든다.** 발견 즉시 프로브를 지웠다.

**운영 함의**: 배포·CI 중에는 AI 응답이 2배 이상 느려진다. YOLO(88ms)는 여유가 있지만
P2PNet은 5초 예산을 확실히 초과한다.

## 7. 티켓 140이 실패 정책을 바꿨다

측정 도중 AI 담당자가 `2a32562`(정확도 우선 이중 모델)를 머지·배포했다. 바뀐 파일은
`ai/app/api/analysis.py` 하나이고 모델·전처리·shadow 서비스는 그대로다 → **지연·RSS·CPU
실측치는 여전히 유효하고, 바뀐 것은 실패 시 응답 의미론이다.**

| | 이전 | 이후(티켓 140) |
|---|---|---|
| 보정 ON + P2PNet 타임아웃 | YOLO 결과 유지, `COMPLETED` | **프레임 전체 `FAILED`** |
| 보정 ON + shadow 비활성 | YOLO 결과 유지 | **전 프레임 `FAILED`** |
| 보정 OFF | 변화 없음 | 변화 없음 |

**위험 조합**: `CORRECTION_ENABLED=true` + `SHADOW_ENABLED=false` → 전 프레임 FAILED.
compose 기본값이 둘 다 false라 현재는 안전하지만, `.env`로 켤 수 있게 만든 뒤로는 두 값을
반드시 함께 넣어야 한다.

별건으로 운영이 **`SPRING_PROFILES_ACTIVE=dev`**로 돌고 있는 것을 확인했다.
`application-dev.yml`은 `ddl-auto: update`이고 `-prod.yml`은 `validate`다. 주말 새 테이블이
마이그레이션 없이 생긴 것이 그 덕분이다. 제출 직전에 `prod`로 바꾸면 스키마가 조금이라도
어긋날 때 backend가 아예 뜨지 않으므로 지금은 건드리지 않는다. 판단은 BE 소유다.

## 8. GPU 노트북 터널로 전환 — 제약과 설계

서버 CPU로는 보정이 불가하다는 결론이 나오자 AI 담당자가 RTX 4050 노트북에서 AI 서버를
돌리고 배포 서버로 리버스 SSH 터널을 연결했다. CLAUDE.md에 "보류"로 적어둔 선택지이고
재검토 조건이 "서버 CPU 실측이 5초 예산을 넘으면"이었으므로 조건이 충족된 것이다.

### 왜 리버스 터널만 가능한가

| 제약 | 실측 |
|---|---|
| 외부 개방 포트 | **443, 8091, 9200뿐**. AWS 콘솔 권한이 없어 추가 개방 불가 |
| 아웃바운드 UDP | SSAFY 망에서 전면 차단 |
| 노트북 인바운드 | 공인 IP 없음 → 서버가 노트북을 직접 호출할 수 없다 |

노트북이 서버로 여는 방향만 가능하다. 제3자 터널 서비스(Cloudflare, ngrok)는 제보 프레임
= 개인정보가 외부를 경유하므로 §16 보안 원칙상 배제했다.

### 터널이 컨테이너에서 보이지 않는 문제

```
노트북 8000  ←ssh -R←  배포 서버 127.0.0.1:18000
```

`sshd_config`의 `GatewayPorts`가 기본값 `no`라 터널은 호스트 루프백에만 바인딩된다.
컨테이너 안의 `127.0.0.1`은 그 컨테이너 자신이므로 backend가 쓸 수 없다.

중계를 만들었다. `network_mode: host`로 도는 nginx 컨테이너가 호스트 루프백에 닿고, 도커
브리지 게이트웨이 `172.17.0.1:18001`에 리스너를 연다. 이미 쓰는 `nginx:alpine`을 재사용해
새 이미지를 받지 않는다. 18000이 아니라 18001인 이유는 18000을 터널이 점유하고 있어서다.

### GPU 성능

| 항목 | GPU 노트북 | 서버 CPU |
|---|---|---|
| YOLO 추론 | **9~38ms** | 63~88ms |
| P2PNet 추론 | 92ms (AI 담당자 보고) | 4,580ms |
| 콜드 스타트 | 2.6~5.7초 | — |
| 최종 경로 왕복(287KB, n=20) | 평균 **273.4ms** p95 **384.7ms** | 96.5 / 105.3ms |

5초 예산의 8%(p95)다. **정확도 우선 모드가 성립한다.**

4K 프레임에서 `congestionSource=P2PNET_CORRECTED`가 나오고 C가 YOLO의 0.0 → **0.47**로
바뀌는 것까지 전체 경로에서 확인했다. 실제 `infra-backend-1` 컨테이너에서 `/health`가
`ready:true`인 것도 확인했다.

**주의: 터널 왕복은 변동이 크다.** 같은 프레임·같은 n=20으로 약 10분 간격을 두고 두 번
측정했는데 평균 548.7ms / p95 1,151.9ms와 평균 273.4ms / p95 384.7ms로 갈렸다. 경로는
두 번째가 홉이 더 많은데 오히려 빨랐다 — 즉 차이는 경로가 아니라 시점이다. 7/30에 측정한
간헐 2.2초 지연과 재전송 0.91%와 같은 성질로 보인다. 서버 안에서 돌 때는 없던 변수다.

## 9. 컨테이너에서 호스트로 가는 트래픽은 ufw INPUT에 걸린다

중계를 띄웠는데 호스트에서는 되고 컨테이너에서는 타임아웃됐다. 원인은 호스트 방화벽이다.

```
iptables -P INPUT DROP   +   ufw active
```

컨테이너 → 호스트 서비스는 FORWARD가 아니라 **INPUT 체인**이다. 컨테이너 간 통신
(minio, db 등)은 FORWARD라 영향이 없어서 이 차이를 놓치기 쉽다.

처음에 `nc -z 172.17.0.1 80`이 성공해서 도달 가능하다고 판단했는데, 80은 ufw에
`ALLOW IN Anywhere` 규칙이 있어서였다. 포트별로 다시 확인했다.

| 대상 | 결과 |
|---|---|
| `172.17.0.1:80` | 도달 (ufw 규칙 있음) |
| `172.17.0.1:18001` | **실패** |
| `172.19.0.1:80` | 도달 |
| `172.19.0.1:18001` | **실패** |

자기 네트워크의 게이트웨이(`172.19.0.1`)여도 막힌다. 규칙을 추가했다.

```bash
sudo ufw allow from 172.16.0.0/12 to 172.17.0.1 port 18001 proto tcp \
  comment 'AI tunnel relay from docker bridges'
```

- 출처 `172.16.0.0/12`: 도커 브리지 대역(172.16~172.31). 인터넷에서 이 출처로 들어올 수 없다
- 목적지 `172.17.0.1`: docker0 게이트웨이. 외부에서 라우팅 불가
- 기존 ufw 규칙 7개는 전부 `ALLOW IN Anywhere`이고 이것만 출처·목적지·포트가 제한돼 있다

**인터페이스 이름(`br-4f5ace7b79f8`)으로 잡지 않았다.** 이름에 네트워크 ID가 들어 있어
`infra_default`가 재생성되면 규칙이 조용히 안 맞게 된다. 출처를 `172.19.0.0/16`으로 좁히는
것도 서브넷이 바뀔 수 있어 피했다.

**우회 불가를 네 가지로 확인했다.** 터널을 `172.17.0.1`에 재바인딩(sshd `GatewayPorts`),
중계를 `172.19.0.1`에 바인딩, 중계를 브리지 컨테이너로, backend를 host 네트워크로 —
전부 안 된다. 앞의 둘은 여전히 호스트 포트라 INPUT에 걸리고, 셋째는 호스트 루프백에 닿지
못하고, 넷째는 DB·Redis·MinIO 접근이 깨진다.

## 10. 자동 폴백 — `upstream`을 쓸 수 없는 이유

AI 담당자가 수동 폴백이 아니라 자동 폴백을 요청했다. 주력은 GPU 터널, 예비는 서버 CPU
`ai-server`, 두 환경 모두 정확도 우선 모드로 같은 정책을 쓴다.

```
backend → nginx:8010
            주력  172.17.0.1:18001 → 중계 → 터널 → 노트북 GPU
            예비  ai-server:8000   → 서버 CPU
```

요청에는 `proxy_next_upstream` 확인이 포함돼 있었는데 이 서버에서는 그 방식을 쓸 수 없다.

`upstream` 블록의 서버 이름은 nginx **기동 시 한 번만** 해석되고, 오픈소스 nginx는 그 안에서
`resolver`를 쓸 수 없다(nginx Plus 기능). 7/31 실장애가 정확히 그 문제였고(2절 참고), 더
나쁜 것은 해석 실패 시 `[emerg] host not found in upstream`으로 **nginx 자체가 기동하지
않는다**는 점이다. `ai-server`가 재생성되는 중에 nginx가 뜨면 사이트 전체가 죽는다.

그래서 변수 `proxy_pass` + `proxy_intercept_errors` + `error_page`로 같은 동작을 만들었다.

```nginx
location / {
    proxy_intercept_errors on;
    error_page 502 503 504 = @ai_backup;
    proxy_pass http://$ai_primary$request_uri;
}
location @ai_backup {
    proxy_pass http://$ai_backup:8000$request_uri;
}
```

`proxy_intercept_errors on`이 필요한 이유: 터널이 끊기면 중계 nginx가 **응답으로** 502를
주는데, 이 지시자가 없으면 업스트림 5xx가 그대로 backend로 흘러간다.

부수 이득이 있다. `max_fails`/`fail_timeout` 방식은 예비로 넘어간 뒤 정해진 시간이 지나야
주력을 다시 시도하는데, 이 방식은 **매 요청이 주력을 먼저 시도**하므로 터널 복구 시 대기
없이 즉시 복귀한다. 대가는 주력이 블랙홀일 때 요청마다 `proxy_connect_timeout`(2초)을 먼저
쓰는 것이다. 연결 거부는 즉시 실패하므로 이 대기가 없다.

### 타임아웃은 안쪽이 더 짧아야 한다

```
AI 내부 P2PNET_SHADOW_TIMEOUT_SECONDS  30s
  < nginx ai-failover proxy_read_timeout  35s
    < backend AI_READ_TIMEOUT  PT40S
```

반대가 되면 앞단이 먼저 끊어 계산을 마친 결과가 버려진다. `AI_READ_TIMEOUT`은 compose가
전달하지 않아 `.env`로 조정할 수 없었으므로 배선을 추가했다(기본값 PT10S로 동작 불변).

### 실제 장애 조건에서 검증됐다

노트북이 밤새 종료되어 터널 리스너가 사라진 상태에서 측정했다. 인위적 재현이 아니다.

| 확인 | 결과 |
|---|---|
| 중계 18001 직접 호출 | HTTP **502** |
| `nginx:8010/health` | HTTP **200**, 본문이 `ai-server` 응답 = 예비로 넘어감 |
| 멀티파트 POST (bus.jpg 134KB) | HTTP 200 `COMPLETED` / personCount 4 / 왕복 166ms |
| 멀티파트 POST (1280×720 280KB) | HTTP 200 `COMPLETED` / 왕복 127ms |

**멀티파트 본문 재전송이 동작한다.** `proxy_request_buffering on`(기본값)이 본문을 끝까지
받아두므로 `error_page`로 예비에 다시 보낼 수 있다. 이것이 이 설계의 가장 불확실한
부분이었는데 실측으로 확인됐다.

**운영 주의**: 자동 폴백은 조용히 성공한다. 주력이 죽어도 겉으로는 정상이라 아무도 모른다.
Uptime Kuma가 `172.17.0.1:18001/health`를 Keyword `"ready":true`로 감시해야 노트북 정지를
알 수 있다.

## 11. 문서 MR 배포가 내 수동 반영분을 지웠다

터널·폴백을 서버에 손으로 반영해 검증한 뒤, 인프라 MR을 올리기 전에 **문서 MR이 먼저
머지·배포됐다.** 그 배포의 `rsync --delete`가 서버를 git 상태로 맞췄다.

| 대상 | 결과 |
|---|---|
| `infra/nginx/ai-failover.conf` | 삭제 → `nginx:8010` Connection refused |
| `infra/nginx-ai-tunnel/` | 디렉터리째 삭제 → 중계 컨테이너의 conf.d가 빈 상태 |
| `docker-compose.yml`의 AI 변수 3개 + `ai-tunnel` 서비스 | 사라짐 |
| backend 환경변수 | `AI_` 없음 → 다시 `http://localhost:8000` |

`.env`와 ufw 규칙은 rsync 대상이 아니라 살아 있었다. **rsync 대상인 `infra/` 안의 파일만**
지워졌다.

7/29에 이미 "동시 rsync가 서로를 덮어쓴다"를 겪고 `resource_group`으로 막았는데, 이번은
동시 실행이 아니라 **git에 없는 파일을 서버에 둔 것** 자체가 원인이다. 지금까지는 "다른
사람 MR이 위험하다"고 봤는데 실제로는 **내 문서 MR이 내 인프라 파일을 지웠다.**

`infra-ai-tunnel-1` 컨테이너는 `restart: unless-stopped` 덕에 살아 있었지만 마운트된 설정
디렉터리가 사라진 좀비 상태였다. 다음 reload나 재시작이면 리스너도 사라진다.

**규칙**: 서버에 손으로 올린 `infra/`·`backend/`·`data/`·`ai/` 파일은 임시라고 생각해야
한다. 검증용으로만 쓰고, 무관한 MR이 하나라도 배포되면 사라진다고 전제한다. 영속시키려면
반드시 커밋해야 한다.

또 하나: compose에 서비스를 추가하는 것만으로는 부족하다. `.gitlab-ci.yml`의
`docker compose up` 대상 목록에도 넣어야 배포가 그 서비스를 만들고 갱신한다. 목록에서
빠지면 배포가 조용히 그 서비스를 건너뛴다.

## 12. 오늘 배운 점

- **"파이프라인 통과"와 "내 변경 반영"은 다른 문제다.** develop에 무엇이 머지됐는지를
  커밋 제목으로 확인해야 한다. 배포는 성공했는데 실어 나른 내용에 내 변경이 없을 수 있다.
- **측정 harness가 시스템을 측정하는지 자신을 측정하는지 확인해야 한다.** 닫힌 루프
  부하 생성기는 동시 영상 2개를 안정적으로 보이게 만들었다.
- **오염된 측정은 편차로 드러난다.** 같은 조건에서 편차 8%와 126%는 다른 이야기를 한다.
  일관성 자체가 유효성의 증거다.
- **같은 배선 누락이 반복된다.** `PUBLIC_DATA_BASE_URL`(7/31)과 `AI_SERVER_BASE_URL`(8/3)은
  같은 병이다. compose 주석에 경위를 적어둔 것으로는 재발을 막지 못했다.
- **상수를 찾으면 매트릭스가 접힌다.** P2PNet 추론이 입력 픽셀 수로 결정되는 상수라는 것을
  알고 나니, 요청받은 측정 항목 10개 중 대부분이 계산으로 답이 나왔다. 다만 그 판단으로
  동료의 요청을 거절하기 전에 비용을 다시 세야 했다 — 3일이라고 본 것이 실제로는 40분이었다.
- **인위적으로 재현하려던 장애가 실제로 일어났다.** 노트북이 밤새 꺼진 것이 폴백 검증의
  가장 좋은 조건이었다. 동시에 단일 실패점이 얼마나 현실적인지도 보여줬다.

## 13. 후속 · 미결

- **인프라 MR 머지**: `feat/S15P11A503-137/ai-gpu-tunnel-failover`(커밋 6개)가 origin에
  푸시돼 있으나 develop에 머지되지 않았다. 머지되면 배포가 파일과 서비스를 복원하고
  `ai-tunnel`을 재생성한다. 그 전까지 AI 경로는 끊긴 상태다(`media_processing_jobs`가
  0행이라 실제 트래픽 영향은 없다).
- **`.env` 정책 반영**: 머지 후 아래 6줄을 넣는다. `CORRECTION_ENABLED=true`를
  `SHADOW_ENABLED=true`와 가중치 URL 없이 넣으면 **모든 프레임이 FAILED가 된다**(7절).
  ```
  AI_SERVER_BASE_URL=http://nginx:8010
  AI_READ_TIMEOUT=PT40S
  P2PNET_SHADOW_ENABLED=true
  P2PNET_CONGESTION_CORRECTION_ENABLED=true
  P2PNET_SHADOW_TIMEOUT_SECONDS=30
  P2PNET_SHADOW_WEIGHT_URL=http://minio:9000/safy-ai-models/p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth
  ```
- **터널 복구 시 주력 자동 복귀 검증**: 노트북이 다시 켜진 뒤 확인한다. 설계상 매 요청이
  주력을 먼저 시도하므로 즉시 복귀해야 한다.
- **Kuma 주력 감시**: `ai-server` 모니터가 `active=0`이다. URL을 `172.17.0.1:18001/health`로
  바꾸고 Keyword `"ready":true`로 감시해야 노트북 정지를 알 수 있다. 자동 폴백이 조용히
  성공하므로 이 감시가 유일한 경로다. Kuma UI 작업이다.
- **터널 자동 재접속**: 노트북 쪽 소유. 어젯밤 끊겼고 사람이 붙어야 하면 8/9 리허설과
  8/10 시연에서 위험하다.
- **진짜 end-to-end**: 제보 → Egress → MinIO → `media_processing_jobs` → 워커 → AI.
  `media_processing_jobs`와 `extracted_frames`가 0행이라 한 번도 실행된 적이 없다. 티켓 95의
  브라우저 라이브 제보 검증과 함께 진행해야 한다.
- **Egress 겹침 메모리**: 계산으로 나오지 않는 유일한 항목이다. 라이브 세션이 필요하다.
  swap 추가 여부도 그전에는 결론내지 않는다.
- **백엔드 워커 처리량**: `MediaProcessingWorker.processPending()`이 `fixedDelay=PT2S`에
  한 번에 최대 10건을 순차 처리한다. GPU 경로(왕복 273ms)에서는 이용률이 약 6%로 여유가
  크지만 폴백 경로(4.6초)에서는 동시 영상 1개에서도 약 99%가 된다. BE 확인 사항이다.
- **스케줄러 스레드 풀**: `PublicDataSchedulerConfig`의 풀 크기가 3인데 `@Scheduled` 작업이
  4개가 됐다(주석은 "혼잡도·행사·재난문자"에 맞춘 것으로 되어 있다). Spring Boot의
  `TaskSchedulingAutoConfiguration`이 `@ConditionalOnMissingBean`이라 커스텀 빈이 있으면
  모든 `@Scheduled`가 그것을 공유한다(코드 근거, 런타임 실측은 아니다). BE 확인 사항이다.
- **운영 프로필 `dev`**: 7절. 제출 전 전환은 위험하다고 보며 BE 판단이 필요하다.
- **`safy-ai` 이미지 누적**: 18개. 단 레이어를 공유하므로 회수 가능한 것은 이미지 2.765GB
  (전체의 8%)와 Build Cache 5.278GB다. 디스크 267G 여유라 급하지 않고, `builder prune`은
  다음 빌드를 느리게 만들므로 제출 후로 미룬다.
- **미머지 브랜치 2개**: `chore/S15P11A503-49/container-restart-policy`(머지 시 redis
  재생성으로 전원 재로그인 — 팀 공지 필요), `docs/S15P11A503-95/webrtc-checklist`.
