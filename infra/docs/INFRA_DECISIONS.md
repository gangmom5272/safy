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
- 팀원 3명(jisu, 세은, 경민) LAN 내 PC 접속 성공

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
