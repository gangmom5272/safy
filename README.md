# SAFY

시민이 제보한 영상과 공공데이터를 함께 분석해 지역 상황과 군중 혼잡도를
판단하는 서비스입니다. SSAFY 특화 프로젝트로 진행합니다.

- 개발 기간: 2026.07.06 ~ 2026.08.14
- 팀명: 오지마삼

## 배포

- 사용자 화면: https://i15a503.p.ssafy.io/app
- 관리자 화면: https://i15a503.p.ssafy.io/admin/login

## 팀원

| 이름 | 역할 |
| --- | --- |
| 김경민 | 팀장, AI & DB |
| 고대영 | Backend |
| 정다희 | Backend |
| 박세은 | Frontend |
| 김건우 | Infra |

## 주요 기능

- 시민 제보 영상 업로드와 실시간 스트리밍
- 제보 영상 프레임 기반 인원 수·혼잡도 자동 분석
- 지역별 위험도 지도와 상황 조회
- 공공데이터(재난문자, 서울 실시간 혼잡도, 서울 행사정보) 연계
- 관리자 대시보드를 통한 제보 검수와 상황 관리
- 대표 프레임 전체 블러 처리를 통한 개인정보 보호

## 서비스 아키텍처

제보 영상은 AI가 영상 전체를 직접 읽지 않고, 약 5초 간격으로 추출한 단일
프레임 단위로 분석합니다.

```text
시민 제보 영상 (OpenVidu)
      │
      ▼
Egress 프레임 추출 (약 5초 간격 JPEG)
      │
      ▼
Backend  ──POST /api/v1/analyze/frame──▶  AI 서버
      │                                     │
      │                              프레임 품질 검사
      │                              YOLO11n 공식 인원 계수
      │                              P2PNet으로 고밀도 C·L 보정
      │                                     │
      ◀────────── 분석 결과(JSON) ──────────┘
      │
프레임별 결과 저장 · 영상 단위 집계 · 최종 위험도 계산
      │
      ▼
Frontend (사용자 앱 / 관리자 웹)
```

- 정상 프레임은 YOLO와 P2PNet을 함께 실행합니다. 공식 인원 수(`personCount`)는
  YOLO 값을 유지하고, 오탐 게이트가 발동하지 않으면 혼잡도(C·L)는 YOLO·P2PNet
  중 더 큰 값을 사용합니다.
- `data` 서버는 재난문자·서울 실시간 혼잡도·서울 행사정보를 수집·정제해
  Backend에 제공합니다.
- 대표 이미지 전체 블러(`POST /api/v1/privacy/blur`)는 원본 이미지를
  프론트에 노출하지 않기 위한 별도 API입니다.

## 기술 스택

**Frontend** (`frontend-user`, `frontend-admin`)
React 19 · TypeScript · Vite · React Router · Zustand · TanStack Query · SCSS(sass-embedded)

**Backend** (`backend`)
Java 21 · Spring Boot 4.1.0

**AI** (`ai`)
FastAPI · YOLO11n(Ultralytics) · P2PNet · PyTorch

**Data** (`data`)
FastAPI · Pydantic v2 · httpx

**Infra** (`infra`)
Docker Compose · Nginx · OpenVidu · Redis · MinIO · Uptime Kuma · GitLab CI/CD

## 프로젝트 구조

```text
frontend-user/    시민용 React/Vite 앱
frontend-admin/   관리자용 React/Vite 웹 (frontend-user와 독립된 프로젝트)
backend/          Java 21 / Spring Boot API, 업무 데이터 저장
ai/               FastAPI 기반 프레임 품질 검사·인원 계수·혼잡도 계산·블러 처리
data/             공공데이터(재난문자·혼잡도·행사정보) 수집·정제
infra/            Docker Compose, Nginx, OpenVidu, MinIO, Redis, CI/CD
docs/             공통 프로젝트 문서
```

## 실행 방법

각 서비스는 독립적으로 실행할 수 있으며, 세부 절차는 서비스별 문서를
참고합니다.

| 서비스 | 실행 절차 |
| --- | --- |
| frontend-user | `cd frontend-user && npm ci && npm run dev` ([상세](frontend-user/README.md)) |
| frontend-admin | `cd frontend-admin && npm ci && npm run dev` |
| backend | `cd backend && ./gradlew bootRun` |
| ai | `cd ai && python -m uvicorn app.main:app --reload` ([상세](ai/README.md)) |
| data | `cd data && python -m uvicorn app.main:app --reload` ([상세](data/README.md)) |

`infra/docker-compose.yml`은 backend·db·redis·minio·ai-server·data·nginx·uptime-kuma를
한 번에 띄웁니다.

```bash
cd infra
docker compose up -d
```

실시간 영상 제보에 쓰는 OpenVidu는 이 compose 파일에 포함되지 않습니다.
OpenVidu Community v3(LiveKit+mediasoup)는 [공식 배포 스택](https://github.com/OpenVidu/openvidu-docker-compose-deployment)으로
별도 실행하고, `OPENVIDU_URL`/`OPENVIDU_API_KEY`/`OPENVIDU_API_SECRET`로만
연결합니다.

## 문서

- [AGENTS.md](AGENTS.md) — 저장소 공통 작업 규칙, 브랜치·커밋 컨벤션
- [ai/docs/backend-integration-contract.md](ai/docs/backend-integration-contract.md) — AI·백엔드 연동 명세
- [ai/docs/production-ai-analysis.md](ai/docs/production-ai-analysis.md) — 현재 운영 중인 AI 분석 최종 명세 (YOLO·P2PNet, C·L 보정, GPU 주력·CPU 폴백)
- [ai/docs/model-license-review.md](ai/docs/model-license-review.md) — 모델·데이터 라이선스 검토
- [backend/docs/grid-risk-api-contract.md](backend/docs/grid-risk-api-contract.md) — 위험도 격자 API 명세
- [infra/docs/INFRA_DECISIONS.md](infra/docs/INFRA_DECISIONS.md) — 인프라 결정 사항
- [infra/docs/WEBRTC_INFRA_ROLE_GUIDE.md](infra/docs/WEBRTC_INFRA_ROLE_GUIDE.md) — OpenVidu/WebRTC 역할 가이드

## 라이선스 및 데이터 고지

현재 SAFY는 SSAFY 교육 과정 프로젝트이며 상업적으로 사용하지 않습니다.

- 공식 인원 계수 모델 `ultralytics`(YOLO)는 AGPL-3.0입니다. 네트워크를 통한
  사용자 상호작용이 조건 발동 기준이므로, 외부 공개 서비스로 전환할 계획이
  생기면 사전 검토가 필요합니다.
- P2PNet 미세 조정에 사용한 JHU-CROWD++ 데이터셋은 이를 인용한 발표
  자료·보고서에 관련 논문 2편 인용을 요구합니다.

자세한 내용은 [ai/docs/model-license-review.md](ai/docs/model-license-review.md)를 참고합니다.
