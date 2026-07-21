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