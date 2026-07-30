# YOLO·P2PNet 동일 표본 비교 기반

## 문서 상태

- 관련 이슈: `S15P11A503-125`
- 상태: 고정 테스트 비교 및 운영 권고 확정
- 운영 공식 모델: `YOLO:yolo11n`
- 보조 모델: `P2PNet:safy-p2pnet-v2-epoch3` (`SHADOW`)
- 자동 모델 전환: 비활성화
- 운영 API 변경: 없음

## 목적

YOLO는 저밀도 장면에서 안정적이지만 고밀도 군중의 사람을 많이 놓치고,
P2PNet은 고밀도 계수에 유리하지만 일부 저밀도 장면에서 큰 오탐을 만든다.
YOLO 결과만으로 고밀도를 판단하면 P2PNet이 필요한 장면을 놓칠 수 있으므로,
같은 정상 프레임을 두 모델로 분석하고 원시 결과를 공정하게 비교하는 기반을
먼저 구현했다.

고정 테스트 결과를 바탕으로 운영 결과는 YOLO만 사용하고 P2PNet은 그림자
모델로 관찰한다. 두 모델 사이의 자동 전환 임계값은 아직 고정하지 않는다.
모든 비교 기록은 다음 상태로 저장한다.

```text
selectionStatus = YOLO_PRIMARY_P2PNET_SHADOW
productionModel = YOLO:yolo11n
secondaryModel = P2PNet:safy-p2pnet-v2-epoch3
secondaryMode = SHADOW
automaticFallback = DISABLED
reason = INSUFFICIENT_RELIABLE_DENSE_SCENE_GATE
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
YOLO 공식·P2PNet 그림자 운영 결정 기록
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

## 고정 테스트 결과

추가 수집·수동 검수한 팀 사진 중 학습과 체크포인트 선택에 사용하지 않은
고정 테스트 29장으로 네 모델을 비교했다.

| 모델 | 계수 MAE | 계수 RMSE | C MAE | L MAE | 평균 처리시간 |
|---|---:|---:|---:|---:|---:|
| YOLO11n | 16.90 | 46.62 | 2.77 | 22.16 | 52.97ms |
| P2PNet SHTechA | 76.86 | 265.24 | 5.45 | 26.55 | 219.90ms |
| P2PNet v1 epoch5 | 39.17 | 127.10 | 3.09 | 20.69 | 219.55ms |
| P2PNet v2 epoch3 | 41.34 | 140.83 | 3.59 | 24.40 | 220.24ms |

테스트 시나리오는 EMPTY 3장, SPARSE 7장, NORMAL 5장,
LOCAL_DENSE 12장, CROWDED 2장이다.

- YOLO는 전체·EMPTY·SPARSE·NORMAL에서 가장 안정적이었다.
- P2PNet v2는 LOCAL_DENSE 계수 MAE `7.33`으로 가장 좋았다.
- CROWDED 계수 MAE는 YOLO `170.0`, P2PNet v2 `34.5`였다.
- `team2-298b28397fe7`에서 정답 7명에 P2PNet v2가 725명을 예측했다.
- CROWDED가 2장뿐이어서 자동 전환 규칙을 확정할 근거로는 부족하다.

## 최종 운영 권고

- 공식 `personCount`, C, L은 YOLO11n 결과만 사용한다.
- P2PNet v2 epoch3는 공식 결과에 영향을 주지 않는 그림자 모델로 실행한다.
- P2PNet 실패 시 내부 실패 코드만 기록하고 YOLO 공식 결과는 유지한다.
- P2PNet 결과를 프론트엔드 또는 관리자 화면의 공식 분석값으로 노출하지 않는다.
- 단순 인원수 임계값에 의한 자동 모델 전환은 비활성화한다.
- 최종 위험도는 기존 합의대로 백엔드가 공식 YOLO 결과로 계산한다.

YOLO는 고밀도 장면에서 사람을 놓치므로 YOLO 예측 인원수만으로 고밀도를
판정할 수 없다. P2PNet 또한 질감이 복잡한 저밀도 장면에서 대형 오탐을 만들 수
있어 P2PNet 예측 인원수만으로 전환할 수도 없다. 현재는 신뢰할 수 있는
고밀도 게이트가 없으므로 그림자 데이터부터 축적한다.

자동 전환은 독립적인 SAFY CROWDED 테스트를 최소 20장 확보하고, 저밀도
대형 오탐·동시 요청 지연시간·GPU 메모리와 고밀도 게이트를 함께 검증한 뒤
재검토한다.

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

- 고정 테스트셋의 CROWDED 표본이 2장뿐이다.
- P2PNet의 질감 기반 대형 오탐 원인을 추가 분석해야 한다.
- 그림자 실행의 동시 요청 성능과 GPU 메모리를 검증해야 한다.
- 운영 자동 전환에는 모델 출력값과 독립적인 고밀도 게이트가 필요하다.
- 처리시간은 모델 초기화와 프레임 품질 검사까지 포함한 API 지연시간이 아니다.
- 새 데이터의 학습·검증·테스트 분할은 같은 영상이나 연속 촬영 그룹 단위로
  분리해야 한다.
