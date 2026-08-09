# 독립 홀드아웃 표본 기반 자동 전환 조건 재검증

## 문서 상태

- 관련 이슈: `S15P11A503-138`
- 상태: 독립성 검증 및 전체 홀드아웃 평가 완료, 자동 전환은 보류 유지
- 운영 공식 모델: `YOLO:yolo11n`
- 보조 모델: `P2PNet:safy-p2pnet-v2-epoch3` (`SHADOW`)
- 자동 모델 전환: 비활성화 유지

## 목적

`S15P11A503-125`(`hybrid-model-comparison.md`)는 고정 테스트셋의 CROWDED
표본이 2장뿐이라 자동 모델 전환 조건을 확정할 근거가 부족하다고 결론지었다.
이 문서는 그 후속으로, 학습·검증에 전혀 사용되지 않은 독립 표본을 최소
20장 이상 확보해 YOLO와 P2PNet의 장단점을 다시 검증하고 자동 전환 가능
여부를 판단한다.

## 독립 표본 선정

새 데이터를 별도로 수집하는 대신, 이미 구축된 `safy-head-point-v1`
데이터셋(`S15P11A503-123`)의 **JHU-CROWD++ TEST 분할 1,589장**을 사용했다.
이 분할은 P2PNet 미세조정(`S15P11A503-124`)의 학습(TRAIN)이나 에포크별
체크포인트 선택(VALIDATION)에 사용된 적이 없다.

### 독립성 검증 방법과 결과

문서 검색만으로 독립성을 확정하지 않고 다음을 코드로 검증했다
(`app/services/jhu_test_holdout.py`,
`scripts/verify_jhu_test_holdout_independence.py`).

1. **manifest 내부 무결성**: TEST 분할의 `sample_id`, `source_group`,
   `image_sha256`가 TRAIN·VALIDATION과 겹치지 않는지 확인한다. 기존
   `validate_head_point_samples()`가 manifest 로드 시마다 이 불변조건을
   강제하므로 이를 재사용해 확인했다.
2. **학습 스크립트 소스 검사**: `scripts/train_p2pnet_finetune.py`와
   `scripts/compare_p2pnet_finetune_checkpoints.py`의 소스 코드에서
   `split="TEST"` 사용 여부를 정적으로 스캔한다.
3. **기존 평가 산출물 교차 검사**: `runs/` 아래 CSV·JSON 46개 파일
   전체에서 TEST 분할의 `sample_id`·`image_sha256`가 등장하는지 스캔한다
   (`runs/datasets/`, `runs/candidate-models/`는 데이터셋 구축물 자체이므로
   제외).

검증 결과:

```text
totalTestSamples: 1589
scenarioCounts: {CROWDED: 1154, EMPTY: 4, LOCAL_DENSE: 344, NORMAL: 67, SPARSE: 20}
structuralCheckPassed: true
trainingScriptTestSplitUsage: {train_p2pnet_finetune.py: false, compare_p2pnet_finetune_checkpoints.py: false}
externalReferenceMatches: {}  (스캔 파일 46개, 일치 0건)
isIndependent: true
```

보고서 원본: `runs/evaluation/jhu-test-holdout-independence-report.json`
(재현 가능한 산출물이므로 Git에는 포함하지 않는다).

ShanghaiTech Part A 182장은 `S15P11A503-75`(`model-count-benchmark.md`)에서
이미 전량 사용되었으므로 이번 독립 평가 근거에서 제외했다. 과거 모델
선택 판단에는 유효하지만 새로운 독립 표본으로 취급하지 않는다.

## 평가 대상 manifest

- **전체 홀드아웃**: `runs/evaluation/jhu-test-holdout-manifest.csv`
  (1,589장 전체, 변경 불가능한 최종 홀드아웃)
- **정성 검수용 균형 subset**: `runs/evaluation/jhu-test-qualitative-subset-manifest.csv`
  (69장, `random_seed=42`로 결정론적 선택. 정답 인원수로 계산한 `scenario`와
  `sample_id` 정렬만 사용해 선택했으며 모델 예측값은 선택 기준에 전혀
  사용하지 않았다.)

| 시나리오 | subset 표본 수 |
|---|---:|
| CROWDED | 25 |
| SPARSE | 20 (전체) |
| LOCAL_DENSE | 10 |
| NORMAL | 10 |
| EMPTY | 4 (전체) |

subset은 빠른 스모크 테스트와 사람이 결과 이미지를 직접 확인하는 정성
검수용이며, 최종 수치 평가는 1,589장 전체 홀드아웃 결과를 기준으로 한다.

## 평가 방법

`app/services/head_point_model_evaluation.py`로 각 표본의 정답 머리 좌표를
정규화해 `calculate_congestion()`에 넣어 **정답 C·L**을 계산하고, 모델이
예측한 좌표(YOLO 중심점 / P2PNet 머리점)로 계산한 **예측 C·L**과 비교했다.
시나리오별 Count MAE·RMSE, C·L MAE, 평균 처리시간과 함께 **macro
평균**(시나리오 단순 평균, 표본 수 불균형에 영향받지 않음)을 별도로
기록했다.

### 대형 오탐(large false positive) 판정 기준

문서화된 운영 임계값이 없어 이번 평가를 위해 다음 기준을 새로 정의했다.
저밀도 시나리오(EMPTY·SPARSE·NORMAL·LOCAL_DENSE)에서 예측치가 정답보다
20명 이상 많고 동시에 3배 이상인 경우로 판정한다. CROWDED는 과다 계수가
계수 오차로 이미 반영되므로 판정 대상에서 제외한다.

### 실행 환경

- GPU: NVIDIA GeForce RTX 4050 Laptop GPU
- P2PNet 가중치: `safy-p2pnet-v2-epoch3` (`epoch-003.pth`, 운영 후보 가중치)
- YOLO 1,589장 실행 시간: 약 53초
- P2PNet 1,589장 실행 시간: 약 5분 3초

## 전체 홀드아웃 결과 (1,589장)

| 시나리오 (표본 수) | 모델 | Count MAE | RMSE | C MAE | L MAE | 평균 처리시간(ms) | 대형 오탐 |
|---|---|---:|---:|---:|---:|---:|---:|
| CROWDED (1,154) | YOLO11n | 425.18 | 909.16 | 44.15 | 80.62 | 14.61 | 0 |
| CROWDED (1,154) | P2PNet v2 | **121.84** | **413.83** | **5.55** | **1.82** | 114.82 | 0 |
| LOCAL_DENSE (344) | YOLO11n | 19.86 | **23.05** | 7.45 | 44.09 | 17.40 | 0 |
| LOCAL_DENSE (344) | P2PNet v2 | **17.80** | 45.40 | **4.20** | **16.77** | 106.89 | 14 (4.1%) |
| NORMAL (67) | YOLO11n | **3.84** | **6.39** | **1.80** | **5.82** | 14.22 | 0 |
| NORMAL (67) | P2PNet v2 | 6.61 | 9.18 | 2.67 | 11.60 | 137.81 | 2 (3.0%) |
| SPARSE (20) | YOLO11n | **1.05** | **1.86** | **0.49** | **4.88** | 14.90 | 0 |
| SPARSE (20) | P2PNet v2 | 40.75 | 103.74 | 9.15 | 25.25 | 67.10 | **4 (20.0%)** |
| EMPTY (4) | YOLO11n | **0.00** | **0.00** | **0.00** | **0.00** | 16.25 | 0 |
| EMPTY (4) | P2PNet v2 | 65.25 | 89.87 | 15.08 | 95.00 | 121.50 | **3 (75.0%)** |

전체(micro, 표본 수 가중) 및 macro(시나리오 단순 평균) 요약:

| 모델 | 전체 Count MAE (micro) | 전체 RMSE (micro) | macro Count MAE | macro RMSE |
|---|---:|---:|---:|---:|
| YOLO11n | 313.26 | 774.86 | 89.99 | 188.09 |
| P2PNet v2 | **93.30** | **353.52** | **50.45** | **132.40** |

결과 원본: `runs/evaluation/jhu-test-holdout-yolo-report.json`,
`runs/evaluation/jhu-test-holdout-p2pnet-report.json`
(재현 가능한 산출물이므로 Git에는 포함하지 않는다).

## 해석

- **CROWDED에서 P2PNet 우위가 대규모 독립 표본으로 재확인됐다.** 이전에는
  2장뿐이던 CROWDED 비교가 1,154장으로 확대됐고, 계수 MAE가 YOLO 대비
  약 71% 낮았다. C·L 오차도 YOLO보다 훨씬 작다.
- **P2PNet의 저밀도 대형 오탐이 수치로 확정됐다.** EMPTY 4장 중 3장(75%),
  SPARSE 20장 중 4장(20%)이 대형 오탐 기준에 해당했다. `hybrid-model-comparison.md`가
  서술로만 남겼던 "질감이 복잡한 저밀도 사진에서 큰 오탐 발생 가능"이
  이번에 정량적 근거를 얻었다.
- **micro 평균과 macro 평균의 차이가 크다.** CROWDED가 전체 표본의
  72.6%를 차지해 micro 평균(YOLO 313.26 vs P2PNet 93.30)은 P2PNet의
  저밀도 약점을 거의 가린다. macro 평균(YOLO 89.99 vs P2PNet 50.45)은
  격차가 훨씬 작게 나타나는데, 이는 시나리오별 성능을 균등하게 반영하기
  때문이다. **표본 불균형이 큰 데이터셋에서는 micro 평균만으로 모델을
  비교하면 안 된다.**
- LOCAL_DENSE는 P2PNet의 Count MAE가 근소하게 낫지만 RMSE는 YOLO보다
  크다(개별 표본에서 큰 오차가 존재). NORMAL·SPARSE·EMPTY는 YOLO가
  뚜렷하게 안정적이다.
- P2PNet은 GPU에서도 YOLO보다 약 7~9배 느리다.

## 현재 결정

이번 검증으로 CROWDED 표본 부족 문제는 해소됐지만, **저밀도 대형 오탐이
독립 표본에서도 재현**되었으므로 다음 결정을 유지한다.

- 공식 `personCount`, C, L은 YOLO11n 결과만 사용한다.
- P2PNet v2 epoch3는 공식 결과에 영향을 주지 않는 그림자 모델로 유지한다.
- 단순 인원수 임계값에 의한 자동 모델 전환은 계속 비활성화한다.
- 자동 전환을 검토하려면 P2PNet 예측치 자체가 아닌, **저밀도 대형 오탐을
  걸러낼 수 있는 별도 게이트**(예: YOLO 결과와의 교차 검증, 질감 기반
  사전 필터)가 먼저 필요하다. 이 게이트 없이는 SPARSE·EMPTY 장면에서
  일부만 P2PNet으로 잘못 전환될 위험이 남아 있다.
- 롤백 기준: 게이트를 추가해 자동 전환을 시범 적용하더라도, 이번 문서의
  저밀도 대형 오탐 비율(EMPTY 75%, SPARSE 20%)보다 악화되면 즉시
  YOLO 단독 운영으로 되돌린다.

## 재현 명령

```bash
python -m scripts.verify_jhu_test_holdout_independence \
  runs/datasets/safy-head-candidates/ready/safy-head-point-v1/manifest.csv \
  --runs-root runs \
  --output runs/evaluation/jhu-test-holdout-independence-report.json

python -m scripts.build_jhu_test_holdout_manifest \
  runs/datasets/safy-head-candidates/ready/safy-head-point-v1/manifest.csv \
  --holdout-output runs/evaluation/jhu-test-holdout-manifest.csv \
  --subset-output runs/evaluation/jhu-test-qualitative-subset-manifest.csv \
  --random-seed 42

python -m scripts.evaluate_head_point_holdout \
  runs/evaluation/jhu-test-holdout-manifest.csv \
  --model yolo \
  --output runs/evaluation/jhu-test-holdout-yolo-report.json

python -m scripts.evaluate_head_point_holdout \
  runs/evaluation/jhu-test-holdout-manifest.csv \
  --model p2pnet \
  --p2pnet-root runs/candidate-models/p2pnet \
  --weight-path runs/p2pnet-finetune-v2-balanced-search/epoch-003.pth \
  --device cuda \
  --output runs/evaluation/jhu-test-holdout-p2pnet-report.json
```

## 한계와 후속 작업

- 이번 홀드아웃은 JHU-CROWD++ 원본 이미지이며, 실제 SAFY 카메라 각도·해상도·
  압축률을 반영하지 않는다.
- EMPTY(4장)·SPARSE(20장)는 표본 수가 적어 대형 오탐 비율의 신뢰구간이
  넓다. 추가 저밀도 독립 표본을 확보하면 비율 추정이 더 안정적이다.
- 이번 평가는 이미지 1장 단위 오프라인 비교이며, 동시 요청 처리량이나
  Egress 트랜스코딩과 겹쳤을 때의 지연시간은 포함하지 않는다
  (`S15P11A503-106`에서 별도 측정).
- 대형 오탐 기준(저밀도 20명·3배)은 이번 평가를 위해 새로 정의한 값이며
  운영 임계값으로 확정된 것은 아니다. 저밀도 게이트를 실제로 설계할 때
  재검토가 필요하다.
- `runs/evaluation/jhu-test-holdout-independence-report.json`을 재생성할
  때는 `verify_jhu_test_holdout_independence.py`를 홀드아웃·subset manifest
  생성 **이전에** 실행해야 한다. 생성 이후에 실행하면 새 manifest 자체가
  스캔 대상에 포함되어 자기 참조로 오탐될 수 있다.

## 저장 원칙

다음 항목은 Git에 포함하지 않는다.

- JHU-CROWD++ 원본 이미지와 머리 좌표 라벨
- P2PNet 외부 코드와 가중치
- 홀드아웃·subset manifest와 평가 결과 JSON

코드(검증·manifest 생성·평가 스크립트와 테스트)만 Git에 포함한다.
