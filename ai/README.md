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
