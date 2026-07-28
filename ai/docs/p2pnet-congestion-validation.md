# P2PNet 머리 좌표 C·L 연결 검증

## 문서 상태

- 관련 이슈: `S15P11A503-122`
- 검증 모델: P2PNet
- 가중치: `SHTechA.pth`
- 비교 모델: YOLO11n
- 혼잡도 계산 규칙: `yolo-grid-v0.2`
- 실행 장치: NVIDIA GeForce RTX 4050 Laptop GPU
- 상태: 검증 완료, 운영 자동 전환 미적용

## 검증 목적

P2PNet이 예측한 머리 점 좌표를 현재 SAFY의 사람 수와 C·L 계산에
연결할 수 있는지 확인한다.

다음 항목을 검증한다.

- P2PNet 머리 점 개수를 `personCount`로 사용할 수 있는지
- 머리 점 좌표를 정규화된 `(x, y)` 좌표로 변환할 수 있는지
- 정규화 좌표를 기존 8×8 C·L 계산에 사용할 수 있는지
- YOLO와 동일한 표본에서 사람 수와 C·L을 비교할 수 있는지
- 일반 장면과 고밀도 장면에서 모델별 특성이 어떻게 달라지는지
- 후속 SAFY 데이터 구성과 미세조정에 사용할 라벨 형식을 결정할
  근거가 있는지

## 구현 방식

P2PNet 출력의 `pred_logits`에서 사람 클래스 점수를 계산하고,
설정한 임계값을 넘은 `pred_points`만 선택한다.

```text
selectedMask = personScore > 0.5
personCount = selectedPoints 개수
```

P2PNet 머리 점은 모델 입력 크기의 픽셀 좌표이므로 다음과 같이
정규화한다.

```text
normalizedX = pointX / resizedWidth
normalizedY = pointY / resizedHeight
```

정규화된 좌표는 `0.0~1.0` 범위로 제한한다.

```text
normalizedX = clamp(normalizedX, 0.0, 1.0)
normalizedY = clamp(normalizedY, 0.0, 1.0)
```

정규화된 머리 점을 기존 `calculate_congestion()`에 전달해
`visualCongestionScore`와 `localPeakCongestionScore`를 계산한다.

## 평가 실행 방법

### 일반·희소·국소 밀집 대표 표본

```bash
python -m scripts.evaluate_p2pnet_congestion \
  runs/yolo-baseline-manifest.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --weight-path runs/candidate-models/p2pnet/weights/SHTechA.pth \
  --output runs/p2pnet-congestion-report.json \
  --device cuda
```

실행 결과:

```text
sampleCount: 12
totalGroundTruthCount: 63
totalPredictedCount: 116
meanAbsoluteError: 5.58
meanProcessingTimeMs: 56.5
```

### ShanghaiTech Part A 고밀도 표본

```bash
python -m scripts.evaluate_p2pnet_congestion \
  runs/evaluation/shanghaitech-part-a-test.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --weight-path runs/candidate-models/p2pnet/weights/SHTechA.pth \
  --output runs/p2pnet-shanghaitech-congestion-report.json \
  --device cuda
```

실행 결과:

```text
sampleCount: 182
totalGroundTruthCount: 78862
totalPredictedCount: 77559
meanAbsoluteError: 57.16
meanProcessingTimeMs: 80.49
```

처리시간은 단일 실행에서 기록된 참고값이다. 모델별 공식 처리 성능
비교는 별도의 모델 계수 성능 및 런타임 성능 측정 결과를 사용한다.

## 일반 대표 표본 결과

총 12장의 EMPTY, SPARSE, NORMAL, LOCAL_DENSE 표본을 비교했다.

### 전체 계수 결과

| 모델 | 표본 수 | 정답 인원 합계 | 예측 인원 합계 | MAE |
|---|---:|---:|---:|---:|
| YOLO11n | 12 | 63 | 53 | 1.17 |
| P2PNet | 12 | 63 | 116 | 5.58 |

일반 대표 표본에서는 YOLO11n의 사람 수 오차가 더 낮다.

### 시나리오별 결과

| 모델 | 시나리오 | 표본 수 | 계수 MAE | 평균 C | 평균 L |
|---|---|---:|---:|---:|---:|
| YOLO11n | EMPTY | 3 | 0.00 | 0.00 | 0.00 |
| P2PNet | EMPTY | 3 | 3.33 | 1.30 | 20.00 |
| YOLO11n | SPARSE | 3 | 1.00 | 0.94 | 12.50 |
| P2PNet | SPARSE | 3 | 2.00 | 1.72 | 12.50 |
| YOLO11n | NORMAL | 3 | 1.33 | 3.28 | 20.00 |
| P2PNet | NORMAL | 3 | 1.67 | 2.81 | 17.50 |
| YOLO11n | LOCAL_DENSE | 3 | 2.33 | 4.06 | 40.00 |
| P2PNet | LOCAL_DENSE | 3 | 15.33 | 8.54 | 83.33 |

현재 SHTechA 가중치의 P2PNet은 일반 장면에서 과대 계수하는 경향이
있다.

특히 다음 문제가 확인됐다.

- 사람이 없는 장면에서도 머리 점을 잘못 예측함
- 국소 밀집 표본에서 사람 수를 크게 과대 계수함
- 과대 계수된 머리 점 때문에 C와 L도 함께 증가함

따라서 현재 P2PNet을 모든 장면의 기본 모델로 사용할 수 없다.

## 고밀도 표본 결과

ShanghaiTech Part A 테스트 표본 182장을 비교했다.

### 사람 수 결과

| 모델 | 표본 수 | 정답 인원 합계 | 예측 인원 합계 | 계수 MAE |
|---|---:|---:|---:|---:|
| YOLO11n | 182 | 78,862 | 812 | 428.85 |
| P2PNet | 182 | 78,862 | 77,559 | 57.16 |

고밀도 표본에서는 P2PNet이 YOLO보다 훨씬 정확하다.

P2PNet은 YOLO 대비 계수 MAE를 약 87% 줄였다.

### C·L 결과

ShanghaiTech 정답 머리 좌표를 정규화해 동일한 C·L 계산 규칙에
적용한 결과와 모델 예측 결과를 비교했다.

| 구분 | 계수 MAE | C MAE | L MAE | 평균 C | 평균 L | L=100 표본 |
|---|---:|---:|---:|---:|---:|---:|
| 정답 머리 좌표 | - | - | - | 61.32 | 99.64 | 177 |
| YOLO11n | 428.85 | 59.22 | 86.00 | 2.09 | 13.64 | 0 |
| P2PNet | 57.16 | 2.90 | 0.18 | 59.82 | 99.60 | 177 |

P2PNet 머리 좌표로 계산한 C는 정답 머리 좌표 기준 C와 매우
가깝게 나타났다.

L은 정답 표본 182장 중 177장이 이미 100점으로 포화되어 있다.
따라서 P2PNet의 낮은 L 오차만으로 모델 정확도가 완벽하다고
판단해서는 안 된다.

고밀도 장면에서는 다음 값을 함께 확인해야 한다.

- `personCount`
- `visualCongestionScore`
- L 점수 포화 여부
- 장면별 머리 점 분포

## 임시 고밀도 구분 후보

현재 표본에서 P2PNet 예측 인원 분포는 다음과 같다.

| 표본군 | 최소 | 5백분위 | 중앙값 | 95백분위 | 최대 |
|---|---:|---:|---:|---:|---:|
| 일반 대표 표본 | 0 | 0.55 | 6.5 | 30.8 | 44 |
| ShanghaiTech 고밀도 | 53 | 113.0 | 310.5 | 1246.55 | 1844 |

`P2PNet predictedCount >= 50`을 사용하면 현재 표본에서는 다음과
같이 구분된다.

```text
일반 대표 표본 선택: 0 / 12
고밀도 표본 선택: 182 / 182
```

하지만 이 값을 운영 임계값으로 확정하지 않는다.

현재 데이터에는 다음 한계가 있다.

- 일반 대표 표본이 12장으로 매우 적음
- 30~65명 수준의 중간 밀도 표본이 부족함
- 실제 SAFY 촬영 각도와 해상도를 충분히 반영하지 못함
- P2PNet 예측값 자체로 모델을 전환하면 P2PNet 추론을 먼저
  실행해야 함
- 데이터셋 차이로 인해 우연히 두 구간이 분리됐을 수 있음

따라서 50명 기준은 후속 데이터 검증을 위한 임시 후보로만 기록한다.

## 현재 결정

현재 운영 모델 구성은 변경하지 않는다.

- 일반·희소·야간·저화질 장면 기본 모델: YOLO11n
- 고밀도 장면 보완 우선 후보: P2PNet
- P2PNet 전체 장면 자동 적용: 보류
- `predictedCount >= 50` 자동 전환: 보류
- YOLO와 P2PNet 동시 실행: 운영 적용 전 추가 검증
- 현재 AI 프레임 분석 API: YOLO 경로 유지

P2PNet 머리 좌표를 personCount와 C·L에 연결하는 기술 경로는
검증됐다. 실제 운영 전환은 SAFY 데이터 구성과 미세조정 이후 다시
결정한다.

## 후속 데이터 및 라벨 결정

P2PNet은 머리 중심점을 직접 예측하고 고밀도 장면에서 유효한 결과를
보였다.

따라서 `S15P11A503-123`에서는 머리 중심점 라벨을 우선 기준으로
검토한다.

필요한 데이터 범위:

- EMPTY
- SPARSE
- NORMAL
- LOCAL_DENSE
- CROWDED
- NIGHT
- LOW_QUALITY
- 30~65명 수준의 중간 밀도 장면
- 실제 SAFY 카메라 각도와 해상도 표본

데이터는 영상 단위로 학습·검증·테스트 세트를 분리한다. 같은 영상의
연속 프레임이 서로 다른 데이터 세트에 섞이지 않도록 한다.

## 현재 한계

- P2PNet 외부 코드는 `runs/`에만 존재함
- P2PNet 가중치는 Git에 포함되지 않음
- SHTechA 가중치는 SAFY 환경에 맞게 미세조정되지 않음
- 일반 대표 표본 수가 적음
- 고밀도 L 점수가 대부분 100으로 포화됨
- 현재 혼잡도 규칙 이름 `yolo-grid-v0.2`는 모델 이름을 포함하지만
  실제 계산 함수는 정규화 좌표를 입력받는 모델 독립 구조임
- 운영 모델 전환과 자동 선택 기준은 아직 구현하지 않음

## 저장 원칙

다음 항목은 Git에 포함하지 않는다.

- P2PNet 외부 저장소 코드
- P2PNet 가중치
- 원본 평가 이미지
- 실행 결과 JSON
- 개인정보가 포함된 실제 SAFY 촬영 표본

외부 코드, 가중치와 실행 결과는 `runs/`에만 저장한다.