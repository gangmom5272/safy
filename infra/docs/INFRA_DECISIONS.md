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