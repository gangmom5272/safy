# YOLO와 후보 모델 계수 성능 비교

## 1. 목적

SAFY의 사람 계수 모델로 사용하는 YOLO11n과 고밀도 군중 보완 후보인
P2PNet을 동일한 환경과 표본에서 비교한다.

다음 항목을 측정해 MVP 기본 모델과 후보 모델의 적용 범위를 결정한다.

- 사람 수 예측 결과
- 정답 대비 절대오차
- 전체 표본 평균 절대오차(MAE)
- 모델 초기화 시간
- 워밍업 이후 평균 추론 시간
- 최대 GPU 할당 메모리
- 장면 유형별 정확도 차이
- 구현 및 운영 복잡도

## 2. 비교 모델

### YOLO11n

- 현재 SAFY FastAPI 분석 API에서 사용하는 기본 모델
- 사람 바운딩 박스, 중심 좌표, 신뢰도를 함께 반환
- 희소·일반 장면 분석과 개인정보 블러 처리에 활용 가능
- 모델 버전: `yolo11n`

### P2PNet

- 사람의 위치를 점 단위로 예측하는 군중 계수 모델
- 바운딩 박스 탐지가 어려운 고밀도 장면의 보완 후보
- ShanghaiTech Part A 사전학습 가중치 사용
- 모델 버전: `SHTechA`
- 외부 코드와 가중치는 `runs/candidate-models/`에만 저장

DM-Count는 74번 이슈에서 추가 후보로 검증했지만, 이번 비교에서는
고밀도 보완 우선 후보로 선정한 P2PNet과 YOLO를 우선 비교했다.

## 3. 실행 환경

- 운영체제: Windows
- Python: 3.11.9
- GPU: NVIDIA GeForce RTX 4050 Laptop GPU 6GB
- PyTorch: 2.13.0+cu130
- CUDA Runtime: 13.0
- YOLO: Ultralytics YOLO11n
- P2PNet 가중치: ShanghaiTech Part A
- 벤치마크 워밍업: 1회
- 표본별 반복 측정: 3회

모든 모델은 같은 노트북과 같은 가상환경에서 측정했다.

## 4. 측정 방법

모델별 측정 조건을 동일하게 맞추기 위해 공통 벤치마크 함수를 사용했다.

1. 모델을 한 번 로드하고 초기화 시간을 별도로 기록한다.
2. 실제 측정 전에 워밍업 추론을 1회 실행한다.
3. 각 표본을 3회 반복 추론한다.
4. CUDA 작업 전후에 동기화하여 비동기 실행 시간을 제외한다.
5. 이미지 파일 읽기 시간은 추론 시간에서 제외한다.
6. 전처리와 모델 추론은 추론 시간에 포함한다.
7. 반복 추론에서 사람 수가 달라지면 오류로 처리한다.
8. GPU 메모리는 PyTorch의 최대 할당 메모리를 기록한다.
9. 동일한 `sample_id`, 장면 유형, 정답 인원수를 가진 결과만 결합한다.

초기화 시간은 로컬 파일과 운영체제 캐시 상태의 영향을 받을 수 있으므로
실제 서버 부팅 시간이나 배포 준비 시간으로 해석하지 않는다.

## 5. 평가 데이터

### ShanghaiTech Part A 테스트 세트

고밀도 장면 비교에는 ShanghaiTech Part A 테스트 세트 전체를 사용했다.

- 표본 수: 182장
- 정답 총인원: 78,862명
- 최소 정답 인원: 66명
- 최대 정답 인원: 2,256명
- 장면 유형: `CROWDED`

이미지와 정답 파일은 Git에 포함하지 않고 다음 경로에 저장한다.

```text
runs/evaluation/shanghaitech/ShanghaiTech/part_A/test_data/
```

### 보완 장면 표본

희소·일반·국소 밀집·야간·저화질 장면은 ShanghaiTech Part B와
그 파생 이미지를 이용해 대표 표본을 구성했다.

| 장면 | 표본 | 정답 인원 | 구성 방법 |
|---|---|---:|---|
| SPARSE | Part B IMG_172 | 11 | 원본 |
| NORMAL | Part B IMG_1 | 23 | 원본 |
| LOCAL_DENSE | Part B IMG_233 | 29 | 원본 |
| NIGHT | Part B IMG_1 | 23 | 밝기를 낮춘 파생 이미지 |
| LOW_QUALITY | Part B IMG_1 | 23 | 블러를 적용한 파생 이미지 |

`NIGHT`와 `LOW_QUALITY`는 실제 야간·저화질 데이터셋이 아니라
동일한 원본에서 생성한 합성 검증 표본이다.

따라서 보완 장면 결과는 모델의 경향을 확인하는 대표 사례이며,
장면별 통계적 성능을 일반화하는 근거로 사용하지 않는다.

## 6. 재현 방법

모든 명령은 `ai` 디렉터리에서 가상환경을 활성화한 뒤 실행한다.

### 6.1 ShanghaiTech Part A 매니페스트 생성

```bash
python -c "from pathlib import Path; from app.services.shanghaitech_dataset import discover_shanghaitech_samples, save_shanghaitech_manifest; root = Path('runs/evaluation/shanghaitech/ShanghaiTech/part_A/test_data'); samples = discover_shanghaitech_samples(root); save_shanghaitech_manifest(samples, Path('runs/evaluation/shanghaitech-part-a-test.csv')); print('sampleCount:', len(samples))"
```

### 6.2 YOLO 벤치마크

```bash
python -m scripts.benchmark_yolo_count \
  runs/evaluation/shanghaitech-part-a-test.csv \
  --output runs/evaluation/yolo-count-benchmark.json \
  --warmup 1 \
  --repetitions 3
```

### 6.3 P2PNet 벤치마크

```bash
python -m scripts.benchmark_p2pnet_count \
  runs/evaluation/shanghaitech-part-a-test.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --weight-path runs/candidate-models/p2pnet/weights/SHTechA.pth \
  --output runs/evaluation/p2pnet-count-benchmark.json \
  --warmup 1 \
  --repetitions 3
```

### 6.4 모델 결과 결합

```bash
python -m scripts.combine_model_benchmarks \
  runs/evaluation/yolo-count-benchmark.json \
  runs/evaluation/p2pnet-count-benchmark.json \
  --output runs/evaluation/model-count-comparison.json
```

평가용 데이터, 모델 가중치, 개별 결과와 결합 결과는 모두 `runs/`에
저장하며 Git에 포함하지 않는다.

## 7. 고밀도 장면 결과

ShanghaiTech Part A 테스트 세트 182장을 동일한 조건으로 측정한 결과다.

| 모델 | 표본 수 | MAE | 초기화 시간(ms) | 평균 추론 시간(ms) | 최대 GPU 메모리(MB) |
|---|---:|---:|---:|---:|---:|
| YOLO11n | 182 | 428.85 | 80.54 | 12.44 | 74.59 |
| P2PNet SHTechA | 182 | 57.16 | 1,787.44 | 92.88 | 765.88 |

### 해석

- 고밀도 장면의 계수 정확도는 P2PNet이 YOLO보다 크게 우수했다.
- YOLO는 고밀도 장면에서 겹치거나 작게 보이는 사람을 많이 놓쳤다.
- P2PNet은 점 단위 예측으로 고밀도 장면의 사람 수를 더 잘 계산했다.
- YOLO의 평균 추론 시간은 P2PNet보다 약 7.5배 빨랐다.
- YOLO의 최대 GPU 할당 메모리는 P2PNet의 약 10분의 1이었다.
- P2PNet의 초기화 시간도 YOLO보다 훨씬 길었다.

예를 들어 ShanghaiTech Part A `IMG_1.jpg`의 결과는 다음과 같다.

- 정답 인원: 172명
- YOLO 예측: 13명
- YOLO 절대오차: 159명
- P2PNet 예측: 218명
- P2PNet 절대오차: 46명

## 8. 보완 장면 결과

각 장면 유형에서 대표 표본 1장을 측정한 결과다.

| 장면 | 정답 | YOLO 예측 | YOLO 오차 | P2PNet 예측 | P2PNet 오차 |
|---|---:|---:|---:|---:|---:|
| SPARSE | 11 | 16 | 5 | 11 | 0 |
| NORMAL | 23 | 23 | 0 | 21 | 2 |
| LOCAL_DENSE | 29 | 14 | 15 | 35 | 6 |
| NIGHT | 23 | 20 | 3 | 13 | 10 |
| LOW_QUALITY | 23 | 15 | 8 | 6 | 17 |

전체 대표 표본 5장의 결과는 다음과 같다.

| 모델 | MAE | 초기화 시간(ms) | 평균 추론 시간(ms) | 최대 GPU 메모리(MB) |
|---|---:|---:|---:|---:|
| YOLO11n | 6.20 | 87.54 | 12.83 | 59.78 |
| P2PNet SHTechA | 7.00 | 1,849.50 | 147.08 | 668.38 |

### 해석

- 일반 장면에서는 YOLO가 정확도와 속도 모두 우수했다.
- 국소 밀집 장면에서는 P2PNet의 오차가 더 작았다.
- 합성 야간·저화질 장면에서는 YOLO의 오차가 더 작았다.
- 희소 장면 1건에서는 P2PNet이 정확했지만 표본이 1장이므로
  전체 희소 장면에 일반화할 수 없다.
- P2PNet이 모든 장면에서 YOLO보다 우수한 것은 아니다.
- 두 모델의 장점이 장면 밀도에 따라 다르게 나타났다.

## 9. 구현 및 운영 비교

### YOLO11n 장점

- 현재 FastAPI 분석 API에 이미 연결되어 있다.
- 사람 수뿐 아니라 바운딩 박스, 중심 좌표, 신뢰도를 반환한다.
- 현재 C·L 혼잡도 계산과 바로 연결된다.
- 얼굴·번호판 등 개인정보 블러 처리 확장에 활용할 수 있다.
- 추론 속도가 빠르고 GPU 메모리 사용량이 작다.
- 희소·일반·야간·저화질 대표 장면에서 안정적인 결과를 보였다.

### YOLO11n 한계

- 사람이 작거나 서로 심하게 겹치는 고밀도 장면에서 미탐지가 많다.
- ShanghaiTech Part A와 같은 초고밀도 계수에는 적합하지 않다.

### P2PNet 장점

- 고밀도 장면의 사람 수 계산 정확도가 YOLO보다 높다.
- 바운딩 박스가 구분되지 않는 장면에서도 점 단위 계수가 가능하다.

### P2PNet 한계

- 추론 시간과 GPU 메모리 사용량이 크다.
- 기존 SAFY API와 별도의 모델 로딩·전처리·후처리가 필요하다.
- 바운딩 박스를 반환하지 않아 개인정보 블러 처리에 직접 사용할 수 없다.
- 외부 구현이 오래된 PyTorch 환경을 기준으로 작성되어 호환 처리가 필요하다.
- 가중치와 코드의 배포·라이선스 조건을 운영 적용 전에 다시 확인해야 한다.

## 10. MVP 적용 결정

현재 MVP의 기본 모델은 `YOLO11n`을 유지한다.

선택 이유는 다음과 같다.

1. 현재 분석 API와 C·L 계산에 이미 연결되어 있다.
2. 일반적인 SAFY 제보 장면에서 정확도와 처리속도의 균형이 좋다.
3. GPU 메모리 사용량이 작아 운영 환경 부담이 낮다.
4. 바운딩 박스와 신뢰도를 후속 기능에 사용할 수 있다.
5. P2PNet은 모든 장면에서 더 정확한 모델이 아니다.

P2PNet은 기본 모델을 교체하지 않고 고밀도 장면의 보완 후보로 유지한다.

향후 실제 SAFY 촬영 데이터가 충분히 확보되면 다음 흐름을 별도 이슈에서
검증할 수 있다.

```text
YOLO 기본 분석
→ 고밀도 또는 낮은 탐지 신뢰도 후보 장면 판별
→ P2PNet 추가 분석
→ 두 결과를 조합하여 최종 계수 결정
```

현재는 고밀도 판단 임계값과 모델 전환 조건을 결정할 실제 데이터가
부족하므로 하이브리드 추론을 즉시 운영 코드에 적용하지 않는다.

## 11. 한계와 후속 작업

- 보완 장면은 유형별 1장이므로 표본을 추가해야 한다.
- 실제 시민 제보 환경의 야간·저화질 표본을 수집해야 한다.
- 카메라 각도, 해상도, 압축률에 따른 영향을 추가 측정해야 한다.
- CPU 배포 환경의 추론 시간을 별도 측정해야 한다.
- 고밀도 전환 조건과 P2PNet 실행 기준을 별도 이슈로 정의해야 한다.
- P2PNet 코드와 가중치의 배포 가능 라이선스를 최종 확인해야 한다.
- 결과 JSON은 재현 자료이며 운영 API 응답에 직접 포함하지 않는다.

## 12. 보안 및 저장 원칙

- ShanghaiTech 원본 데이터는 Git에 포함하지 않는다.
- 외부 모델 코드와 가중치는 Git에 포함하지 않는다.
- 벤치마크 JSON과 시각화 결과는 `runs/`에만 저장한다.
- 실제 시민 제보 원본이나 개인정보 포함 이미지는 커밋하지 않는다.
- 신뢰할 수 있는 공식 출처의 가중치만 로드한다.