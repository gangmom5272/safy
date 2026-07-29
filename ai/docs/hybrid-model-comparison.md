# YOLO·P2PNet 동일 표본 비교 기반

## 문서 상태

- 관련 이슈: `S15P11A503-125`
- 상태: 오프라인 비교 기반 구현 및 1차 검증 완료
- 자동 모델 선택: 미적용
- 운영 API 변경: 없음

## 목적

YOLO는 저밀도 장면에서 안정적이지만 고밀도 군중의 사람을 많이 놓치고,
P2PNet은 고밀도 계수에 유리하지만 일부 저밀도 장면에서 큰 오탐을 만든다.
YOLO 결과만으로 고밀도를 판단하면 P2PNet이 필요한 장면을 놓칠 수 있으므로,
같은 정상 프레임을 두 모델로 분석하고 원시 결과를 공정하게 비교하는 기반을
먼저 구현했다.

이번 작업에서는 데이터 부족 상태에서 임계값을 고정하지 않는다. 모든 비교
기록은 다음 상태로 저장한다.

```text
selectionStatus = UNDECIDED
reason = INSUFFICIENT_SAFY_EVALUATION_DATA
```

## 처리 흐름

```text
정답 라벨이 있는 프레임
        ↓
이미지 바이트 1회 로드
        ├─ YOLO 분석
        └─ P2PNet 분석
                ↓
모델별 결과와 차이 기록
                ↓
시나리오별 MAE·RMSE 리포트 생성
                ↓
자동 모델 선택은 보류
```

두 모델은 같은 GPU에서 안전하게 측정하기 위해 순차 실행하지만 동일한 이미지
바이트를 입력으로 사용한다. 한 모델이 실패해도 다른 모델의 성공 결과는
버리지 않는다.

## 기록 항목

각 모델에 대해 다음 값을 기록한다.

- `modelName`
- `modelVersion`
- `modelWeightId`
- `analysisStatus`
- `personCount`
- `visualCongestionScore`
- `localPeakCongestionScore`
- `processingTimeMs`
- `failureCodes`

두 모델 사이의 다음 차이도 함께 계산한다.

- `personCountDifference`
- `visualCongestionScoreDifference`
- `localPeakCongestionScoreDifference`

현재 평가 manifest에는 정답 인원수만 있으므로 사람 수 MAE·RMSE는 계산할 수
있지만 C·L의 정답 오차는 계산하지 않는다. 머리 좌표 정답을 사용하는 후속
평가에서 C·L 오차를 추가할 수 있다.

## 실패 처리

모델 실행은 서로 독립적으로 처리한다.

```text
YOLO 성공 + P2PNet 실패
→ YOLO 결과 보존
→ P2PNet status = FAILED
→ failureCodes = ["MODEL_INFERENCE_FAILED"]

YOLO 실패 + P2PNet 성공
→ P2PNet 결과 보존
→ YOLO status = FAILED
→ failureCodes = ["MODEL_INFERENCE_FAILED"]
```

실제 예외 내용은 보고서에 넣지 않고 서버 로그에만 남긴다. 이 오프라인 비교
도구는 재시도를 수행하지 않으며 운영 재시도 정책도 변경하지 않는다.

## 현재 팀 검증 결과

수동 검수가 끝난 팀 사진 중 검증 분할 11장에 YOLO11n과 124번에서 선택한
P2PNet 에포크 5 가중치를 실행했다.

| 모델 | 표본 수 | MAE | RMSE | 평균 처리시간 |
|---|---:|---:|---:|---:|
| YOLO11n | 11 | 1.09 | 1.48 | 60.82ms |
| P2PNet `safy-p2pnet-v1` | 11 | 13.09 | 33.16 | 244.45ms |

시나리오별 결과는 다음과 같다.

| 시나리오 | 표본 수 | YOLO MAE | P2PNet MAE |
|---|---:|---:|---:|
| EMPTY | 2 | 2.00 | 0.50 |
| SPARSE | 7 | 0.43 | 19.86 |
| NORMAL | 1 | 2.00 | 3.00 |
| LOCAL_DENSE | 1 | 3.00 | 1.00 |

P2PNet은 EMPTY와 LOCAL_DENSE에서 더 정확했지만, SPARSE 사진 한 장을 실제
4명에서 111명으로 예측했다. YOLO는 같은 사진을 5명으로 예측했다. 따라서
현재 데이터로 P2PNet의 예측 인원이나 두 모델의 차이에 임계값을 설정하면
일부 저밀도 장면을 고밀도로 잘못 선택할 위험이 있다.

124번 학습 보고서와 이번 결과의 수치가 다른 이유는 평가 경로가 다르기
때문이다. 124번은 학습용 검증 데이터 로더의 크기 정렬 전처리를 사용했고,
이번 작업은 실제 P2PNet 추론 어댑터와 같은 전처리를 사용했다. 운영 후보를
판단할 때는 이번 비교 경로를 기준으로 다시 평가해야 한다.

## 현재 결정

- 두 모델을 같은 표본에서 비교하는 오프라인 도구는 유지한다.
- 자동 선택 규칙과 운영 API 연동은 적용하지 않는다.
- P2PNet 에포크 5를 운영 모델로 확정하지 않는다.
- SAFY 사진을 시나리오별로 확보하고 고정 테스트셋을 구성한 뒤 재평가한다.
- 새 데이터가 준비되면 동일 도구로 YOLO, 기존 P2PNet, 미세 조정 P2PNet을
  비교하고 최종 모델 또는 조합을 결정한다.

## 재현 명령

### 팀 검증셋 전체 비교

```bash
python -m scripts.evaluate_hybrid_models \
  runs/datasets/safy-head-candidates/ready/safy-head-point-finetune-v1/manifest.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --p2pnet-weight-path runs/p2pnet-finetune-balanced-search/selected.pth \
  --output runs/hybrid-model-comparison-team-validation.json \
  --device cuda \
  --source TEAM_CONSENTED \
  --split VALIDATION
```

### 일부 표본 스모크 테스트

```bash
python -m scripts.evaluate_hybrid_models \
  runs/datasets/safy-head-candidates/ready/safy-head-point-finetune-v1/manifest.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --p2pnet-weight-path runs/p2pnet-finetune-balanced-search/selected.pth \
  --output runs/hybrid-model-comparison-smoke.json \
  --device cuda \
  --source TEAM_CONSENTED \
  --split VALIDATION \
  --limit 2
```

## 한계와 후속 검증

- 팀 검증셋이 11장뿐이며 CROWDED 표본이 없다.
- NORMAL과 LOCAL_DENSE도 각각 1장뿐이다.
- 같은 데이터가 모델 선택 과정에 사용되었으므로 독립 테스트셋이 필요하다.
- 처리시간은 모델 초기화와 프레임 품질 검사까지 포함한 API 지연시간이 아니다.
- 새 데이터의 학습·검증·테스트 분할은 같은 영상이나 연속 촬영 그룹 단위로
  분리해야 한다.
