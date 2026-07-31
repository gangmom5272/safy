# SAFY 프로젝트·AI 개발 인수인계

> 최종 갱신: 2026-07-30
>
> 저장소: `C:\Users\SSAFY\Desktop\S15P11A503`
>
> 이 문서는 Claude Code가 새 대화에서도 프로젝트 목적, 현재 구현 상태,
> 협업 방식과 다음 작업을 한 번에 파악하기 위한 진입점이다.

@AGENTS.md

## 1. 가장 먼저 지킬 것

1. 답변과 진행 안내는 한국어로 한다.
2. 작업 전 `git branch --show-current`와 `git status --short`를 확인한다.
3. `main`이나 `develop`에서 직접 수정하지 않는다.
4. 사용자가 명시적으로 요청하지 않으면 커밋, 푸시, 병합, Jira 변경을 하지
   않는다.
5. 기본 협업 방식은 사용자가 직접 코드를 작성할 수 있도록 한 단계씩 정확한
   파일명, 삽입 위치와 전체 코드 블록을 제공하는 것이다.
6. 사용자가 `알아서 해줘`, `직접 작성해줘`, `수정해줘`처럼 명시하면 직접
   구현하고 검증한다. 사용자 결정이 필요한 아키텍처·보안·외부 권한 문제에서는
   임의로 정하지 말고 멈춰서 함께 결정한다.
7. 사용자는 코드 블록이 여러 개로 갈라지는 것을 불편해한다. 복사해야 하는
   코드는 가능한 한 하나의 완전한 코드 블록으로 제공한다.
8. 사용자가 `정상`, `저장했어`, `통과`라고 답하면 이전 단계를 반복 설명하지
   말고 다음 단계로 진행한다.
9. 원본 사진, 학습 데이터, 모델 가중치, 토큰, PEM 키, `.env`는 Git에 넣지
   않는다.
10. 인프라·백엔드·프론트 범위를 건드리기 전 변경 이유와 합의할 내용을 먼저
    설명한다.

## 2. 프로젝트 개요

SAFY는 시민 제보 영상과 공공데이터를 이용해 지역 상황과 군중 혼잡도를
분석하는 서비스다.

- `frontend-user`: 시민용 React/Vite 앱
- `frontend-admin`: 관리자용 React/Vite 웹
- `backend`: Java 21/Spring Boot API와 업무 데이터 저장
- `ai`: FastAPI 기반 프레임 품질 검사, 사람 계수, 혼잡도 계산, 블러 처리
- `infra`: Docker Compose, Nginx, OpenVidu, MinIO, Redis, CI/CD
- `data`: 공공데이터 수집·정제

영상 전체를 AI가 직접 읽지 않는다. OpenVidu/Egress 영역에서 약 5초 간격으로
JPEG 프레임을 추출하고, AI는 전달받은 이미지 한 장씩 분석한다.

```text
제보 영상
→ OpenVidu/Egress 프레임 추출
→ 단일 JPEG
→ POST /api/v1/analyze/frame
→ 품질 검사
→ YOLO 공식 분석
→ 조건이 허용되면 P2PNet 그림자 분석
→ 백엔드가 프레임별 결과 저장·영상 단위 집계·최종 위험도 계산
```

중요: Egress가 MinIO에 JPEG를 올리는 구현은 있으나, 현재 백엔드에는 AI HTTP
클라이언트와 설정이 없다. 누가 언제 MinIO 프레임을 AI API로 POST할지는 아직
최종 배선되지 않았다. 자세한 내용은 `미해결 사항`을 참고한다.

## 3. AI API와 책임 범위

### 프레임 분석

```text
POST /api/v1/analyze/frame
Content-Type: multipart/form-data
field: image
```

주요 응답 필드:

- `analysisStatus`: `COMPLETED`, `INVALID_FRAME`, `FAILED`
- `personCount`: 공식 사람 수
- `personConfidences`: API에는 존재하지만 백엔드 DB에는 저장하지 않기로 합의
- `visualCongestionScore`: 전체 시각적 혼잡도 C
- `localPeakCongestionScore`: 국소 최대 혼잡도 L
- `qualityIssues`: 품질 문제 코드 배열
- `failureCodes`: 실패 코드 배열
- `modelName`, `modelVersion`, `modelWeightId`
- `congestionRuleVersion`, `frameQualityRuleVersion`
- `processingTimeMs`
- `confidence`: 전체 분석 신뢰도이며 현재 보정 근거가 없어 `null`

상태 규칙:

```text
COMPLETED
  qualityIssues = []
  failureCodes = []

INVALID_FRAME
  personCount/C/L = null
  qualityIssues = 1개 이상
  failureCodes = []
  재시도하지 않음

FAILED
  personCount/C/L = null
  qualityIssues = []
  failureCodes = 1개 이상
  백엔드가 1회 재시도
```

품질 코드:

```text
DECODE_FAILED
LOW_RESOLUTION
TOO_DARK
TOO_BRIGHT
TOO_BLURRY
```

AI 내부 실패 코드:

```text
MODEL_INFERENCE_FAILED
CONGESTION_CALCULATION_FAILED
INTERNAL_AI_ERROR
```

백엔드 통신 실패 코드:

```text
AI_CONNECTION_FAILED
AI_TIMEOUT
INVALID_AI_RESPONSE
```

실패한 프레임을 사람 0명으로 계산하지 않는다. `INVALID_FRAME`과 최종 실패한
`FAILED`는 영상 단위 통계에서 제외한다. 최종 위험도와 영상 단위 상태·집계는
백엔드 책임이다.

### 전체 이미지 블러

```text
POST /api/v1/privacy/blur
```

사람이나 얼굴 일부만 찾는 방식이 아니라 대표 이미지 전체에 Gaussian blur를
적용한다. 백엔드가 분석 성공 프레임 중 대표 이미지 한 장을 선정하여 블러 API에
전달한다. 원본 이미지는 프론트에 노출하지 않는다.

## 4. 혼잡도와 프레임 품질 규칙

- 혼잡도 규칙: `yolo-grid-v0.2`
- 프레임 품질 규칙: `frame-quality-v0.3`
- 프레임을 8×8 격자로 나눈다.
- C는 64개 셀 점수의 평균이다.
- L은 가장 혼잡한 2×2 영역의 인원을 먼저 합산한 뒤 점수화한다.
- L은 2×2의 인원 평균이 아니라 국소 영역 전체 혼잡도를 표현한다.
- C와 L은 실제 명/㎡가 아닌 SAFY 내부 상대 점수다.

품질 기준의 핵심:

- 최소 해상도는 320×240 수준으로 보정되었다.
- 지나치게 어둡거나 밝거나 흐린 프레임은 분석에서 제외한다.
- 품질 검사 실패는 사람이 없는 정상 프레임과 구분한다.
- `blurScore`는 측정 전에만 짧은 변을 720px로 축소한다(`v0.3`). 정규화
  없이는 같은 사진이 해상도에 따라 `TOO_BLURRY`로 뒤집힌다. 운영 프레임
  폭은 백엔드가 `LIVE_FRAME_WIDTH:1280`으로 고정하므로 짧은 변이 720이며
  정규화 대상이 아니다.

관련 문서:

- `ai/docs/congestion-scoring.md`
- `ai/docs/congestion-validation.md`
- `ai/docs/frame-quality-validation.md`
- `ai/docs/video-frame-analysis.md`

## 5. 모델 전략

### 공식 모델: YOLO11n

- 저밀도·일반 장면에서 상대적으로 안정적이다.
- 운영의 공식 `personCount`, C, L은 현재 YOLO 결과만 사용한다.
- 고밀도 군중에서는 가려진 사람을 많이 놓친다.

### 보조 모델: P2PNet

- 머리 중심 좌표를 점으로 예측한다.
- 고밀도 군중 계수와 C·L 계산에 유리하다.
- 질감이 복잡한 저밀도 사진에서는 매우 큰 오탐이 발생할 수 있다.
- 현재 모델은 `safy-p2pnet-v2-epoch3`이다.
- 자동 모델 전환은 비활성화되어 있다.
- P2PNet 결과는 공식 결과에 영향을 주지 않는 `SHADOW` 모드로 기록한다.
- P2PNet 실패 시 YOLO 공식 응답은 유지한다.

고정 테스트 29장 결과:

| 모델 | Count MAE | Count RMSE | C MAE | L MAE |
|---|---:|---:|---:|---:|
| YOLO11n | 16.90 | 46.62 | 2.77 | 22.16 |
| P2PNet SHTechA | 76.86 | 265.24 | 5.45 | 26.55 |
| P2PNet v1 epoch5 | 39.17 | 127.10 | 3.09 | 20.69 |
| P2PNet v2 epoch3 | 41.34 | 140.83 | 3.59 | 24.40 |

- YOLO는 전체·EMPTY·SPARSE·NORMAL에서 안정적이었다.
- P2PNet v2의 LOCAL_DENSE Count MAE는 `7.33`으로 가장 좋았다.
- CROWDED Count MAE는 YOLO `170.0`, P2PNet v2 `34.5`였다.
- CROWDED 테스트가 2장뿐이고 P2PNet의 저밀도 대형 오탐이 있어 자동 전환
  기준을 아직 만들 수 없다.

관련 문서:

- `ai/docs/candidate-model-validation.md`
- `ai/docs/model-count-benchmark.md`
- `ai/docs/p2pnet-congestion-validation.md`
- `ai/docs/p2pnet-fine-tuning.md`
- `ai/docs/hybrid-model-comparison.md`
- `ai/docs/p2pnet-shadow-analysis.md`

## 6. 학습 데이터와 가중치

P2PNet 미세 조정 데이터셋:

- 총 이미지: 4,395장
- 총 머리 좌표: 1,497,912개
- JHU-CROWD++ v2.0: 4,344장
- 사용 동의를 받고 수동 승인한 팀 사진: 51장
- 팀 사진 135장 중 자동 좌표만 존재하는 84장은 학습에서 제외했다.
- 데이터와 라벨은 `runs/`에만 존재하며 Git에 포함하지 않는다.

운영 후보 가중치:

```text
local path:
ai/runs/p2pnet-finetune-v2-balanced-search/epoch-003.pth

deployment version:
safy-p2pnet-v2-epoch3

size:
82.39MB

sha256:
934989b1ec644660f3924fb916418cb7f757b11f35b470af46c324ac18204857
```

P2PNet 외부 소스:

```text
repository:
https://github.com/TencentYoutuResearch/CrowdCounting-P2PNet.git

pinned commit:
5c91a81ca062b1c7fd3db3ad1c55b1c21f0a7455
```

현재 학업·비상업 용도는 P2PNet, JHU-CROWD++, 팀 사진 모두 허용 범위 안이다.
다만 공식 모델의 `ultralytics`는 AGPL-3.0이므로 상업성과 무관하게 네트워크
배포가 조건 발동 기준이다. 외부 공개 서비스로 열 계획이 생기면 먼저 논의한다.
JHU-CROWD++ 약관 조항 6은 발표 자료와 보고서에 논문 2편 인용을 요구한다.
자세한 내용은 `ai/docs/model-license-review.md`를 참고한다.

가중치는 Git에 커밋하지 않는다.

## 7. 완료된 주요 AI 이슈

| 이슈 | 완료 내용 |
|---|---|
| 74 | 후보 군중 계수 모델 적용 가능성 검증 |
| 75 | YOLO와 P2PNet 계수 정확도·속도·GPU 메모리 비교 |
| 76 | C·L 국소 혼잡도 규칙을 `yolo-grid-v0.2`로 보정 |
| 77 | 프레임 품질 기준 검증 및 `frame-quality-v0.2` 보정 |
| 78 | 분석 신뢰도·모델·가중치·규칙 버전 기록 기준 구현 |
| 97 | 영상 프레임 상태, 품질 코드, 실패 코드 처리 구현 |
| 99 | 단일·연속 프레임 런타임 벤치마크 구현 |
| 121 | 대표 이미지 전체 블러 API 구현 |
| 122 | P2PNet 머리 좌표를 C·L 계산에 연결하고 검증 |
| 123 | P2PNet 미세 조정용 머리 좌표 데이터셋 구축 |
| 124 | P2PNet 미세 조정과 균형 체크포인트 선정 |
| 125 | YOLO·P2PNet 동일 표본 비교와 그림자 운영 기준 확정 |
| 136 | P2PNet 그림자 분석·비교 로그 구현 |
| 137 | 진행 중: P2PNet 운영 배포 자산과 CPU 컨테이너 구성 |

## 8. 현재 Git 상태와 137번 진행 상황

현재 브랜치:

```text
feat/#S15P11A503-137/p2pnet-deployment-assets
```

이미 커밋·푸시된 커밋:

```text
69e4814 Feat: [S15P11A503-137] [AI] P2PNet 운영 배포 자산 구성
```

이 커밋에는 다음이 포함된다.

- `ai/Dockerfile`
- `ai/.dockerignore`
- P2PNet 자산 manifest와 SHA-256 검증
- 고정 외부 소스 커밋과 호환성 패치
- 가중치 다운로드·영속 캐시
- 자산 실패 시 P2PNet만 비활성화하고 YOLO/API 유지
- Compose AI 서비스와 모델 볼륨
- 배포 문서와 테스트

주의: 최초 커밋은 GPU 서버를 가정했다. 인프라 확인 결과 실제 운영 서버에는
GPU가 없으므로 아래 CPU 전환 변경이 **현재 로컬에만 있고 아직 커밋·푸시되지
않았다.**

현재 로컬 변경:

- `ai/requirements-deploy.txt` 신규: CPU 운영 배포 의존성
- Dockerfile이 `requirements-deploy.txt` 설치
- Compose의 NVIDIA 장치 예약 제거
- `P2PNET_SHADOW_DEVICE=cpu`
- 코드의 기본 P2PNet 장치도 `cpu`
- CPU 성능과 RSS 측정 결과 문서화
- requirements 3종의 역할 주석 정리

검증 상태:

```text
pytest: 198 passed
ruff check: passed
git diff --check: passed
```

이 로컬 변경은 사용자의 명시적 요청 전까지 커밋·푸시하지 않는다.

## 9. CPU 배포 사전 실측

운영 서버 사양:

```text
host: i15a503.p.ssafy.io
CPU: 4 vCPU
memory: 15GB, 현재 여유 약 12GB
swap: 0
GPU/NVIDIA runtime: 없음
AWS 콘솔 권한: 없음
```

노트북에서 `CUDA_VISIBLE_DEVICES=-1`로 GPU를 숨기고 측정했다.

측정 노트북 CPU:

```text
13th Gen Intel Core i7-13700H
14 cores / 20 logical processors
```

| 항목 | CPU 결과 |
|---|---:|
| YOLO 대표 표본 평균 | 57.31ms |
| YOLO 대표 표본 P95 | 68.81ms |
| YOLO 고밀도 평균 | 59.93ms |
| YOLO 고밀도 P95 | 78.46ms |
| P2PNet 일반 표본 평균 | 679.52ms |
| P2PNet 고밀도 표본 평균 | 1725.92ms |
| P2PNet 초기화 | 1.95~3.57초 |
| YOLO+P2PNet 최고 관측 RSS | 약 1.25GB |
| P2PNet 추론 후 RSS | 약 832MB |

5초 프레임 간격에는 여유가 있어 CPU 서버 단독 배포를 먼저 검증한다. 그러나
노트북 CPU가 서버 4 vCPU보다 강하므로 최종 결론은 운영 서버 컨테이너에서
같은 벤치마크를 실행한 뒤 확정한다.

현재 결정:

```text
1차: 운영 서버 CPU 컨테이너 하나
보류: GPU 노트북 병행/리버스 터널
조건: 서버 CPU 실측이 5초 예산을 넘으면 GPU 노트북 가속기 재검토
```

## 10. 인프라 현황과 배포 원칙

- 운영 서버에는 GPU, NVIDIA 드라이버, NVIDIA Container Toolkit이 없다.
- `runtime: nvidia`, `--gpus`, GPU device reservation을 사용하면 기동 실패한다.
- 학습과 GPU 실험은 RTX 4050 노트북에서 계속한다.
- 운영 컨테이너는 CPU 전용 PyTorch wheel을 사용해야 한다.
- 기존 자동 배포 명령에는 아직 `ai-server`가 포함되지 않는다.
- 인프라 담당자가 Compose·CI 배선과 영구 모델 볼륨을 최종 연결할 예정이다.
- 서버 swap이 0이므로 실제 컨테이너 RSS와 동시 부하를 확인한 뒤 swap 추가
  여부를 인프라 담당자가 판단한다.
- P2PNet 실패는 전체 AI/API 실패가 아니다. YOLO 공식 분석은 유지한다.

가중치 저장 방식은 MinIO로 확정했다.

- 최초 137 구현(GitLab Generic Package Registry + Deploy Token)은 폐기했다.
- 확정: 기존 MinIO 재사용, bucket `safy-ai-models`,
  object 경로 `p2pnet/{deploymentVersion}/{fileName}`
- 가중치는 82.39MB지만 Git 저장소 비대화를 막기 위해 Git 커밋은 하지 않는다.
- MinIO 원본을 컨테이너 시작 시 체크섬 검증 후 영구 볼륨(`p2pnet_model_cache`)에
  캐시한다.
- 다운로드는 presigned URL(권장) 또는 nginx 헤더 검증 방식 중 하나를
  사용한다. 코드(`p2pnet_assets.py`)는 URL과 단일 인증 헤더만 받는 범용
  다운로더라 두 방식 모두 코드 변경 없이 지원한다.
- 자세한 내용은 `ai/docs/p2pnet-deployment-assets.md`의 "가중치 저장 정책"을
  참고한다.

## 11. 반드시 해결할 미해결 사항

### 11.1 프레임을 누가 AI로 보내는가

현재 가장 중요한 통합 차단 요소다.

- Egress는 5초 간격 JPEG를 MinIO에 올린다.
- AI API `POST /api/v1/analyze/frame`은 준비되어 있다.
- 백엔드에는 AI URL 설정과 HTTP 클라이언트가 없다.
- 다음 중 하나를 백엔드·OpenVidu 담당자와 결정해야 한다.

```text
A. 프레임 생성 이벤트마다 백엔드가 즉시 AI 호출
B. 세션 종료 후 백엔드가 MinIO 프레임을 순차 배치 호출
C. 별도 워커가 MinIO 프레임을 소비하여 AI 호출
```

실시간에 가까운 결과가 필요하면 A, 구현 단순성과 안정성이 우선이면 B가
현실적이다. 결정 없이 임의로 구현하지 않는다.

### 11.2 LiveKit AI 토큰 코드

백엔드에 `issueAiToken` 코드가 있으나 호출 컨트롤러가 없다. 원래 AI가
LiveKit을 직접 구독하는 설계의 잔재일 수 있다. 현재 HTTP 이미지 업로드 방식과
방향이 다르므로 백엔드와 사용 여부를 확인하고, 사용하지 않는다고 확정되기 전에는
임의 삭제하지 않는다.

### 11.3 운영 서버 실측

CPU Docker 이미지가 서버에서 빌드된 뒤 다음을 다시 측정한다.

- YOLO 평균/P95/최대 지연시간
- P2PNet 일반·고밀도 지연시간
- 모델 초기화 시간
- 컨테이너 RSS
- 동시 요청 시 대기열과 timeout 비율
- Egress 트랜스코딩과 겹쳤을 때 메모리

### 11.4 최종 자동 배포

다음이 끝나기 전까지 `ai-server`를 기존 deploy 서비스 목록에 넣지 않는다.

- 백엔드 AI 호출 배선
- 모델 저장소와 인증 방식 확정
- 서버 CPU 실측
- health check와 장애 격리 확인
- 인프라 담당자 MR 검토

## 12. 주요 경로

```text
ai/app/api/analysis.py                 프레임 분석 API
ai/app/api/privacy.py                  전체 이미지 블러 API
ai/app/schemas/analysis.py             분석 응답 스키마와 상태 검증
ai/app/services/person_detector.py     YOLO 사람 탐지
ai/app/services/congestion.py          C·L 계산
ai/app/services/frame_quality.py       프레임 품질 판정
ai/app/services/shadow_analysis.py     P2PNet 그림자 실행과 비교 로그
ai/app/services/p2pnet_assets.py       운영 자산 다운로드·검증·장애 격리
ai/app/services/p2pnet_benchmark_adapter.py  P2PNet 모델 로드·추론
ai/deploy/p2pnet-assets.json           모델/소스/checksum manifest
ai/Dockerfile                          AI 운영 이미지
infra/docker-compose.yml               서비스 배치
.gitlab-ci.yml                          CI/CD
```

데이터, 모델, 실행 결과:

```text
ai/runs/
```

`runs/`는 로컬 전용이다. 개인 사진, JHU 데이터, ShanghaiTech 데이터,
체크포인트와 벤치마크 JSON을 Git에 추가하지 않는다.

## 13. 개발·검증 명령

AI 작업 디렉터리:

```powershell
cd "C:\Users\SSAFY\Desktop\S15P11A503\ai"
```

전체 테스트와 정적 검사:

```powershell
& ".\.venv\Scripts\python.exe" -m pytest -q
& ".\.venv\Scripts\python.exe" -m ruff check .
& ".\.venv\Scripts\python.exe" -m ruff format --check $(
  git ls-files --modified --others --exclude-standard -- "*.py"
)
git diff --check
```

로컬 AI 서버:

```powershell
$env:P2PNET_SHADOW_ENABLED = "false"
& ".\.venv\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

다른 PowerShell에서 헬스체크:

```powershell
curl.exe http://127.0.0.1:8000/health
```

PowerShell에서 CPU 강제 측정 시 빈 문자열은 환경 변수를 제거하므로 사용하지
않는다. 반드시 `-1`을 쓴다.

```powershell
$env:CUDA_VISIBLE_DEVICES = "-1"
& ".\.venv\Scripts\python.exe" -c "import torch; print(torch.cuda.is_available())"
```

출력은 `False`여야 한다.

대표 YOLO CPU 벤치마크:

```powershell
$env:CUDA_VISIBLE_DEVICES = "-1"
& ".\.venv\Scripts\python.exe" -m scripts.benchmark_frame_runtime `
  runs/yolo-baseline-manifest.csv `
  --output runs/runtime-performance-cpu-representative.json `
  --warmup 3 `
  --repetitions 10
```

고밀도 YOLO CPU 벤치마크:

```powershell
$env:CUDA_VISIBLE_DEVICES = "-1"
& ".\.venv\Scripts\python.exe" -m scripts.benchmark_frame_runtime `
  runs/evaluation/shanghaitech-part-a-test.csv `
  --output runs/runtime-performance-cpu-crowded.json `
  --warmup 3 `
  --repetitions 3
```

## 14. Git·MR 규칙

브랜치 예시:

```text
feat/#S15P11A503-137/p2pnet-deployment-assets
fix/#S15P11A503-137/<short-slug>
chore/#S15P11A503-137/<short-slug>
```

AI 최근 커밋 형식:

```text
Feat: [S15P11A503-137] [AI] P2PNet 운영 배포 자산 구성
Fix: [S15P11A503-75] [AI] CI SciPy 의존성 누락 수정
```

커밋 전 순서:

```text
1. git branch --show-current
2. git status --short --untracked-files=all
3. pytest
4. ruff check
5. ruff format --check
6. git diff --check
7. 필요한 파일만 git add
8. git diff --cached --check
9. git diff --cached --stat
10. 사용자 확인 또는 명시적 커밋 요청
```

- 커밋되지 않은 사용자 변경을 덮어쓰거나 정리하지 않는다.
- `git reset --hard`, 무단 checkout 복원, 강제 push를 사용하지 않는다.
- MR에는 목적, 주요 변경, 검증 결과, 인프라·백엔드 확인사항을 쓴다.
- CI가 통과해도 인프라 파일이 바뀌었으면 담당자 검토 전 병합하지 않는다.
- 자동 배포 때문에 develop MR 병합 시간이 길 수 있다.

## 15. requirements 파일 역할

```text
ai/requirements.txt
  RTX 4050 노트북 학습·로컬 GPU 검증
  CUDA 13.0 PyTorch

ai/requirements-ci.txt
  GPU 없는 GitLab CI 테스트
  CPU PyTorch + pytest + ruff

ai/requirements-deploy.txt
  운영 서버 CPU 컨테이너
  CPU PyTorch + 런타임 의존성만
```

공통 라이브러리 버전을 변경할 때 세 파일의 정합성을 함께 확인한다.

## 16. 보안·데이터 원칙

- PEM 키 내용, Deploy Token, MinIO 비밀번호를 출력하거나 커밋하지 않는다.
- 인증 값은 GitLab CI/CD Variables 또는 서버의 Git 제외 `.env`에만 둔다.
- 다운로드 URL이나 예외 로그에 인증 정보가 포함되지 않게 한다.
- 잘못된 가중치는 SHA-256 검증 실패 후 사용하지 않는다.
- 다운로드는 임시 파일에 받고 검증 성공 후 원자적으로 캐시에 교체한다.
- AI 내부 예외 메시지와 스택 트레이스는 API 사용자에게 노출하지 않는다.
- 실제 제보 이미지와 팀원 사진은 Git에 넣지 않는다.
- 외부 데이터셋과 P2PNet 라이선스를 문서화하고 사용 범위를 준수한다.

## 17. 다음 Claude가 이어서 할 순서

1. 루트에서 현재 브랜치와 `git status`를 확인한다.
2. 137번 CPU 전환 로컬 diff를 읽고 사용자 변경을 보존한다.
3. 사용자가 요청하면 CPU 전환 변경을 스테이징·커밋·푸시한다.
4. 인프라 담당자에게 CPU 실측값, 가중치 82.39MB, 최고 RSS 1.25GB를
   전달한다.
5. ~~가중치를 MinIO에 저장할지 GitLab Package Registry에 저장할지 최종
   결정한다.~~ MinIO로 확정했다(§10 참고). 인프라가 `safy-ai-models`
   버킷에 실제 게시하고 presigned URL 또는 헤더 검증 방식을 정하면
   서버 `.env`에 반영한다.
6. 인프라가 CPU 컨테이너를 운영 서버에 올리면 동일 벤치마크를 서버에서
   재실행한다.
7. 서버 결과가 5초 예산과 메모리 기준을 통과하면 CPU 단독 배포를 확정한다.
8. 백엔드·OpenVidu 담당자와 프레임→AI 호출 주체를 결정한다.
9. 호출 배선과 장애 격리까지 확인한 후에만 자동 deploy 목록에 `ai-server`를
   추가한다.
10. GPU 노트북 병행 구성은 서버 CPU가 부족하다는 실측이 나올 때만 다시
    검토한다.

## 18. 문서 우선순위

상세 정보가 필요하면 다음 순서로 읽는다.

1. `CLAUDE.md` — 전체 상황과 현재 작업
2. `AGENTS.md` — 저장소 공통 작업 규칙
3. `ai/README.md` — AI 실행과 기능 범위
4. `ai/docs/hybrid-model-comparison.md` — 모델 운영 결정
5. `ai/docs/p2pnet-shadow-analysis.md` — 그림자 운영 구현
6. `ai/docs/p2pnet-deployment-assets.md` — 137 배포 자산
7. `ai/docs/runtime-performance-benchmark.md` — GPU·CPU 성능
8. `ai/docs/video-frame-analysis.md` — 프레임 상태·실패 계약
9. `ai/docs/model-license-review.md` — 모델·데이터 라이선스 제약

이 문서와 코드가 다르면 코드를 먼저 확인하고, 차이를 사용자에게 알린 뒤 문서를
갱신한다. 날짜가 바뀌었거나 브랜치가 변경되었다면 이 문서의 `현재 Git 상태`를
그대로 믿지 말고 반드시 Git 명령으로 재확인한다.
