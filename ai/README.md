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
- GPU 추론 처리시간 측정

C·L 계산은 SAFY MVP를 위한 임시 규칙입니다. 계산식과 한계는
[C·L 임시 혼잡도 계산 규칙](docs/congestion-scoring.md)을 참고합니다.

혼잡 지속성 P, 혼잡 증가 추세 G, 최종 위험도 계산과 개인정보 블러는 후속 구현 범위입니다.

## 후보 군중 계수 모델 검증

SAFY의 기본 사람 탐지 모델과 고밀도 군중 보완 후보를 비교했다.

- MVP 기본 모델: YOLO11n
- 고밀도 보완 우선 후보: P2PNet
- 밀도 맵 기반 추가 비교 후보: DM-Count
- 검증용 외부 코드, 가중치 및 결과 이미지는 `runs/`에만 저장

상세한 실행 결과와 적용 판단은
[후보 군중 계수 모델 적용 가능성 검증](docs/candidate-model-validation.md)에서 확인할 수 있다.