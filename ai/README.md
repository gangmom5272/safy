# SAFY AI Server

SAFY 시민 제보 이미지에서 사람을 탐지하고 분석 결과를 JSON으로 반환하는 FastAPI 서버입니다.

## Environment

- Python 3.11.9
- PyTorch 2.13.0+cu130
- torchvision 0.28.0+cu130
- Ultralytics 8.4.102
- NVIDIA GeForce RTX 4050 Laptop GPU

## Setup

```bash
cd ai
python -m venv .venv
source .venv/Scripts/activate
python -m pip install --upgrade pip setuptools wheel
python -m pip install -r requirements.txt
```

YOLO 모델 파일은 Git에 포함하지 않습니다. `ai/weights/yolo11n.pt` 경로에 배치해야 합니다.

## Run

```bash
python -m uvicorn app.main:app --reload
```

- Swagger UI: http://127.0.0.1:8000/docs
- Health check: http://127.0.0.1:8000/health

## Analyze a frame

```bash
curl -X POST "http://127.0.0.1:8000/api/v1/analyze/frame" \
  -F "image=@runs/bus.jpg"
```

## Render congestion debug image

YOLO 사람 탐지 결과와 C·L 계산 근거를 디버그 이미지로 확인합니다.

```bash
python -m scripts.render_congestion_debug runs/bus.jpg \
  --output runs/bus-congestion-debug.png
```

디버그 이미지에는 다음 정보가 표시됩니다.

- 8×8 혼잡도 격자
- YOLO 사람 바운딩 박스
- 칸별 사람 수와 점수
- L 계산에 선택된 최대 2×2 영역
- 전체 사람 수와 C·L 요약

원본 이미지와 디버그 결과는 `runs/`에 저장하며 Git에 포함하지 않습니다.

자세한 검증 결과는
[혼잡도 계산 및 디버그 시각화 검증](docs/congestion-validation.md)을 참고합니다.


## Evaluate YOLO baseline

COCO128 대표 표본으로 YOLO 사람 계수 정확도와 C·L 결과를
평가하고 JSON 보고서를 생성합니다.

```bash
python -m scripts.evaluate_yolo_baseline \
  docs/yolo-baseline-manifest.example.csv \
  --output runs/yolo-baseline-report.json
```

평가 이미지와 결과 JSON은 `runs/`에 저장하며 Git에 포함하지
않습니다.

표본 선별 기준, 기준 평가 결과와 제한사항은
[YOLO 기준 모델 평가](docs/yolo-baseline-evaluation.md)를 참고합니다.



## Test

```bash
python -m pytest
python -m ruff check .
```

## Current scope

- 이미지 파일 입력 및 프레임 품질 검사
- YOLO 사전학습 모델의 `person` 클래스 탐지
- `personCount` 및 탐지별 confidence 반환
- YOLO 바운딩 박스 중심점 기반 임시 C·L 계산
- 전체 시각적 혼잡도 `visualCongestionScore` 반환
- 국소 최대 혼잡도 `localPeakCongestionScore` 반환
- `COMPLETED`, `INVALID_FRAME`, `FAILED` 분석 상태 처리
- 표준 품질 문제 코드와 AI 내부 실패 코드 반환
- GPU 추론 처리시간 측정
- 단일 요청 및 연속 프레임의 지연시간·FPS·GPU 메모리 측정

C·L 계산은 대표 표본 검증으로 보정한 `yolo-grid-v0.2` 규칙을 사용합니다.
계산식, 보정 근거와 한계는
[C·L 혼잡도 계산 규칙](docs/congestion-scoring.md)을 참고합니다.

개별 사람 탐지 신뢰도와 전체 분석 신뢰도는 구분합니다.
현재 전체 분석 신뢰도는 보정 근거가 없어 `null`을 반환하며,
모델·가중치·분석 규칙 버전은 모든 분석 상태에 포함합니다.
상세한 필드 의미와 상태별 JSON 명세는
[분석 신뢰도와 모델 버전 기록 기준](docs/analysis-confidence-versioning.md)을 참고합니다.

영상 프레임은 OpenVidu 측에서 5초 간격으로 추출하며,
AI 서버는 전달받은 단일 프레임의 품질 검사와 분석만 담당합니다.
프레임 상태, 실패 코드, 재시도 및 영상 단위 집계 기준은
[영상 프레임 분석 상태 및 실패 처리 기준](docs/video-frame-analysis.md)을 참고합니다.

단일 요청과 연속 프레임의 지연시간, FPS 및 GPU 메모리를 측정하며,
대표 표본과 고밀도 표본을 이용한 측정 결과와 1차 운영 기준은
[AI 프레임 분석 런타임 성능 측정](docs/runtime-performance-benchmark.md)을 참고합니다.

혼잡 지속성 P, 혼잡 증가 추세 G, 최종 위험도 계산과 개인정보 블러는 후속 구현 범위입니다.

## 후보 군중 계수 모델 검증

SAFY의 기본 사람 탐지 모델과 고밀도 군중 보완 후보를 비교했다.

- MVP 기본 모델: YOLO11n
- 고밀도 보완 우선 후보: P2PNet
- 밀도 맵 기반 추가 비교 후보: DM-Count
- 검증용 외부 코드, 가중치 및 결과 이미지는 `runs/`에만 저장

상세한 실행 결과와 적용 판단은
[후보 군중 계수 모델 적용 가능성 검증](docs/candidate-model-validation.md)에서 확인할 수 있다.

## YOLO와 후보 모델 계수 성능 비교

동일한 평가 표본과 GPU 환경에서 YOLO11n과 P2PNet의 계수 정확도,
초기화 시간, 추론 시간 및 GPU 메모리 사용량을 비교했습니다.

- 일반적인 SAFY 장면의 MVP 기본 모델: YOLO11n
- 고밀도 장면의 보완 후보: P2PNet
- ShanghaiTech Part A 고밀도 표본: P2PNet의 계수 정확도가 우수
- 일반·야간·저화질 대표 표본: YOLO의 정확도와 운영 효율이 우수
- 현재 결정: YOLO11n을 기본 모델로 유지하고 P2PNet은 보완 후보로 유지

상세한 평가 조건, 재현 방법, 장면별 결과와 모델 선택 근거는
[YOLO와 후보 모델 계수 성능 비교](docs/model-count-benchmark.md)에서 확인할 수 있습니다.

## P2PNet 머리 좌표 C·L 연결 검증

P2PNet이 예측한 머리 점을 정규화된 좌표로 변환해 기존 8×8 C·L
계산에 연결했습니다.

- 일반·희소 장면 기본 모델: YOLO11n 유지
- 고밀도 장면 보완 우선 후보: P2PNet
- ShanghaiTech Part A 계수 MAE: YOLO `428.85`, P2PNet `57.16`
- ShanghaiTech Part A C MAE: YOLO `59.22`, P2PNet `2.90`
- P2PNet의 전체 장면 적용과 자동 전환은 아직 적용하지 않음
- 중간 밀도 및 실제 SAFY 데이터 확보 후 전환 기준 재검증 예정

연결 방식, 재현 명령, 시나리오별 결과와 현재 결정은
[P2PNet 머리 좌표 C·L 연결 검증](docs/p2pnet-congestion-validation.md)을
참고합니다.

## P2PNet 미세 조정용 머리 좌표 데이터셋

P2PNet의 저밀도 장면 오탐을 보완하기 위해 JHU-CROWD++와 사용 동의를 받은 팀 사진을 `head-point-v1` 형식으로 정제했습니다.

- 최종 이미지: 4,395장
- 최종 머리 좌표: 1,497,912개
- JHU-CROWD++: 4,344장
- 수동 검수한 팀 사진: 51장
- 데이터와 라벨은 라이선스 및 용량 문제로 `runs/`에만 저장
- 이미지 해시, 좌표 범위, 인원수, EXIF 방향과 분할 누수 검증
- P2PNet 초기 좌표는 수동 승인 전까지 학습 데이터에서 제외

데이터 출처, 머리 좌표 작성 기준, 보정·제외 정책과 재현 명령은
[P2PNet 미세 조정용 머리 좌표 학습 데이터셋](docs/head-point-dataset.md)을 참고합니다.

## P2PNet 미세 조정

승인된 팀 사진과 JHU-CROWD++를 함께 사용해 P2PNet을 미세 조정하고,
에포크별 체크포인트의 저밀도·고밀도 계수 성능을 비교했습니다.

- 팀 검증 MAE: `90.27 → 17.09`
- JHU 검증 MAE: `100.77 → 86.47`
- JHU 검증 RMSE: `316.57 → 311.85`
- 세 지표를 모두 개선한 에포크 5를 균형 후보로 선정
- 가중치와 실행 결과는 `runs/`에만 저장하고 운영 모델은 아직 교체하지 않음

학습 구성, 손실 키 보정, 체크포인트 비교와 선정 근거는
[P2PNet 미세 조정 및 체크포인트 선정](docs/p2pnet-fine-tuning.md)을 참고합니다.

## YOLO·P2PNet 동일 표본 비교

같은 평가 프레임에 YOLO11n과 미세 조정 P2PNet을 실행하고 모델별 사람 수,
C·L, 처리시간, 실패 코드와 예측 차이를 기록하는 오프라인 도구를 제공합니다.

- 한 모델이 실패해도 다른 모델의 성공 결과를 보존
- 시나리오별 MAE·RMSE 비교
- 일반 평가 manifest와 `head-point-v1` manifest 지원
- 운영 공식 모델: YOLO11n
- 보조 관찰 모델: P2PNet v2 epoch3 (`SHADOW`)
- 자동 모델 전환: `DISABLED`
- 고정 테스트 29장 계수 MAE: YOLO `16.90`, P2PNet v2 `41.34`
- P2PNet v2의 LOCAL_DENSE 계수 MAE `7.33`, CROWDED 계수 MAE `34.5`
- 운영 분석 API와 공식 모델 가중치는 변경하지 않음

고정 테스트 결과, 그림자 운영 방식과 자동 전환을 보류한 근거는
[YOLO·P2PNet 동일 표본 비교 기반](docs/hybrid-model-comparison.md)을 참고합니다.

## P2PNet 그림자 분석

운영 API의 공식 결과는 YOLO11n으로 유지하면서, 정상 분석 프레임에 한해
P2PNet v2 epoch3를 백그라운드에서 선택적으로 실행할 수 있습니다.

- 기본 설정: 비활성화
- 공식 응답 모델: YOLO11n
- 그림자 관찰 모델: P2PNet v2 epoch3
- 비교 항목: 사람 수, C·L, 처리시간, 상태와 실패 코드
- 자동 모델 전환 및 P2PNet 결과의 API 응답 반영: 비활성화
- `INVALID_FRAME` 및 YOLO 분석 실패 프레임: 그림자 분석 제외
- P2PNet 초기화·추론 실패 또는 시간 초과: YOLO 공식 결과에 영향 없음

환경 변수, 비교 로그 형식, 실패 격리 정책과 백엔드 연동 범위는
[P2PNet 그림자 분석 운영 기준](docs/p2pnet-shadow-analysis.md)을 참고합니다.

P2PNet 외부 코드는 고정 커밋으로 컨테이너에 포함하고, 운영 가중치는
MinIO(`safy-ai-models` 버킷)에서 내려받아 체크섬 검증 후 영속 볼륨에
캐시합니다. 운영 서버는 CPU 전용 이미지로 기동하며, 가중치 게시,
비활성화와 롤백 절차는
[P2PNet 운영 자산 및 CPU 배포](docs/p2pnet-deployment-assets.md)를 참고합니다.

## 프레임 품질 기준 검증

프레임 품질 검사는 사람 탐지 전에 해상도, 밝기와 블러 상태를 판정합니다.

현재 적용 규칙은 `frame-quality-v0.2`입니다.

- 최소 짧은 변: 240px
- 최소 전체 픽셀: 320×240
- 최소 밝기 점수: 35
- 최대 밝기 점수: 220
- 최소 블러 점수: 80

평가 매니페스트의 기대 결과와 실제 품질 판정을 비교하려면 다음 명령을
실행합니다.

```bash
python -m scripts.evaluate_frame_quality \
  docs/frame-quality-manifest.example.csv \
  --output runs/evaluation/frame-quality-report.json


  ## Blur a representative image

백엔드가 정상 분석 프레임 중 대표 이미지 한 장을 선정하면,
AI 서버가 전달받은 이미지 전체를 블러 처리해 JPEG로 반환합니다.

```bash
curl -X POST \
  "http://127.0.0.1:8000/api/v1/privacy/blur" \
  -F "image=@runs/bus.jpg" \
  --output runs/bus-blurred.jpg
```

대표 프레임 선정은 백엔드가 담당하며, AI 서버는 제보별 상태나
프레임 간 분석 결과를 저장하지 않습니다.

대표 프레임 선정 기준, 전체 처리 흐름, 실패 코드와 보안 원칙은
[대표 이미지 전체 블러 처리](docs/image-blur.md)를 참고합니다.
