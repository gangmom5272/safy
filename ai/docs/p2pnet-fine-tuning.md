# P2PNet 미세 조정 및 체크포인트 선정

## 문서 상태

- 관련 이슈: `S15P11A503-124`
- 모델 이름: `P2PNet`
- 미세 조정 모델 버전: `safy-p2pnet-v1`
- 기반 가중치: `SHTechA.pth`
- 상태: 오프라인 검증 완료, 운영 연동 전

## 목표

ShanghaiTech Part A로 학습된 기존 P2PNet은 고밀도 장면에서는 YOLO보다
계수 성능이 좋지만, SAFY의 EMPTY·SPARSE 장면에서는 배경을 사람으로
오탐하는 문제가 있었다.

이번 작업은 다음 목표로 P2PNet을 미세 조정했다.

- 동의받은 팀 사진으로 SAFY 촬영 환경을 학습에 반영
- EMPTY·SPARSE 장면의 오탐 감소
- JHU-CROWD++ 검증 성능을 함께 확인해 고밀도 성능 저하 방지
- 에포크별 체크포인트를 저장하고 균형 성능으로 최종 후보 선정

## 데이터셋

`S15P11A503-123`에서 구축한 `head-point-v1` 데이터셋을 사용했다.

| 구분 | 이미지 수 | 용도 |
|---|---:|---|
| JHU-CROWD++ v2.0 | 4,344 | 다양한 밀도의 머리 좌표 학습·외부 검증 |
| TEAM_CONSENTED | 51 | SAFY 환경 학습·검증 |
| 합계 | 4,395 | 미세 조정 데이터 |

팀 사진은 시나리오를 유지하는 계층 분할로 40장을 학습, 11장을 검증에
사용했다.

| 팀 검증 시나리오 | 이미지 수 |
|---|---:|
| EMPTY | 2 |
| SPARSE | 7 |
| NORMAL | 1 |
| LOCAL_DENSE | 1 |

수집한 팀 사진은 총 135장이지만, 수동으로 머리 좌표를 승인한 51장만
학습 데이터에 포함했다. P2PNet이 자동 생성한 좌표만 존재하는 나머지
84장은 라벨 오류가 학습에 들어가는 것을 막기 위해 제외했다.

이미지, 좌표 라벨, 가중치와 실행 결과는 라이선스 및 용량 문제로
`runs/`에만 저장하고 Git에는 포함하지 않는다.

## 학습 구성

| 항목 | 값 |
|---|---:|
| 에포크 | 8 |
| 배치 크기 | 1 |
| 패치 크기 | 128×128 |
| 이미지당 패치 수 | 4 |
| 에포크당 표본 수 | 256 |
| 팀 사진 샘플링 비율 | 0.3 |
| 모델 헤드 학습률 | `1e-5` |
| 백본 학습률 | `1e-6` |
| 가중치 감쇠 | `1e-4` |
| 최대 그래디언트 노름 | `0.1` |
| 판정 임계값 | `0.5` |
| 난수 시드 | `42` |
| 데이터 로더 워커 | `0` |

학습 표본은 머리 중심 양성 패치와 배경 패치로 만든다. 팀 사진이 전체
데이터에서 차지하는 비율이 매우 작으므로 균형 샘플러로 매 에포크의
30%를 팀 사진에서 선택한다.

검증 이미지는 P2PNet FPN의 텐서 크기 불일치를 방지하기 위해 긴 변을
최대 1,536px로 제한하고 가로·세로를 128의 배수로 정렬한다. EXIF 방향을
적용한 뒤 머리 좌표도 같은 비율로 변환한다.

외부 P2PNet 코드의 손실 이름은 `loss_point`지만 기본 가중치 사전에는
`loss_points`가 사용되어 좌표 손실이 총 손실에서 빠질 수 있었다. 학습
전에 키를 `loss_point`로 정규화해 분류 손실과 좌표 손실을 모두 반영한다.

## 체크포인트 비교 결과

기존 `SHTechA.pth`와 8개 에포크 체크포인트를 같은 기준으로 비교했다.
팀 검증셋은 저밀도 오탐 개선을, JHU 검증셋 496장은 기존 군중 계수 성능
유지를 확인하는 용도다.

| 모델 | 팀 MAE | JHU MAE | JHU RMSE |
|---|---:|---:|---:|
| 기존 SHTechA | 90.27 | 100.77 | 316.57 |
| 에포크 1 | 49.00 | 95.04 | 304.51 |
| 에포크 2 | 30.09 | 87.54 | 295.67 |
| 에포크 3 | 8.45 | 84.42 | 332.29 |
| 에포크 4 | 15.45 | 86.36 | 316.96 |
| **에포크 5** | **17.09** | **86.47** | **311.85** |
| 에포크 6 | 19.82 | 87.73 | 326.67 |
| 에포크 7 | 13.91 | 90.07 | 331.37 |
| 에포크 8 | 2.64 | 98.67 | 367.15 |

에포크 8은 팀 검증 MAE가 가장 낮지만 JHU RMSE가 `367.15`로 증가해
고밀도 장면의 큰 오차가 악화됐다. 팀 MAE만 보고 선택하면 SAFY의 핵심
군중 장면 성능을 잃을 수 있으므로 사용하지 않는다.

에포크 5는 기존 모델과 비교해 세 지표를 모두 개선했다.

- 팀 MAE: `90.27 → 17.09` (`73.18` 감소)
- JHU MAE: `100.77 → 86.47` (`14.30` 감소)
- JHU RMSE: `316.57 → 311.85` (`4.72` 감소)

팀 검증셋의 시나리오별 결과도 다음과 같다.

| 시나리오 | 표본 수 | 기존 MAE | 에포크 5 MAE |
|---|---:|---:|---:|
| EMPTY | 2 | 24.50 | 0.50 |
| SPARSE | 7 | 134.29 | 26.14 |
| NORMAL | 1 | 2.00 | 3.00 |
| LOCAL_DENSE | 1 | 2.00 | 1.00 |

## 현재 결정

에포크 5 체크포인트를 저밀도와 고밀도 성능 사이의 균형 후보로 선정한다.
실행 디렉터리에서는 다음 경로로 복사해 식별한다.

```text
runs/p2pnet-finetune-balanced-search/selected.pth
```

- 파일 크기: `86,393,995 bytes`
- SHA-256: `638e4c59c4fe0c96c02f9777403411755cac5347ec0c2dc5e9e5c9f5ad104e8f`

이번 이슈에서는 학습·비교·후보 선정까지만 수행한다. 운영 API가 사용하는
가중치를 자동으로 교체하지 않으며, 배포 반영은 별도 이슈에서 모델 버전,
가중치 전달 방식, 롤백 기준과 함께 결정한다.

## 재현 방법

### 1. 팀 검증 분할 생성

```bash
python -m scripts.create_p2pnet_finetune_manifest \
  runs/datasets/safy-head-candidates/ready/safy-head-point-v1/manifest.csv \
  --output runs/datasets/safy-head-candidates/ready/safy-head-point-finetune-v1/manifest.csv \
  --validation-ratio 0.2 \
  --random-seed 42
```

### 2. 데이터셋 검증

```bash
python -m scripts.validate_head_point_dataset \
  runs/datasets/safy-head-candidates/ready/safy-head-point-finetune-v1/manifest.csv
```

### 3. 미세 조정

```bash
python -m scripts.train_p2pnet_finetune \
  runs/datasets/safy-head-candidates/ready/safy-head-point-finetune-v1/manifest.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --weight-path runs/candidate-models/p2pnet/weights/SHTechA.pth \
  --output runs/p2pnet-finetune-balanced-search \
  --device cuda \
  --epochs 8 \
  --batch-size 1 \
  --patch-count 4 \
  --samples-per-epoch 256 \
  --team-sampling-ratio 0.3 \
  --validation-source TEAM_CONSENTED \
  --num-workers 0
```

### 4. 에포크별 외부 검증

```bash
python -m scripts.compare_p2pnet_finetune_checkpoints \
  runs/datasets/safy-head-candidates/ready/safy-head-point-finetune-v1/manifest.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --baseline-weight-path runs/candidate-models/p2pnet/weights/SHTechA.pth \
  --checkpoint-directory runs/p2pnet-finetune-balanced-search \
  --output runs/p2pnet-finetune-balanced-search/checkpoint-comparison.json \
  --device cuda \
  --validation-source 'JHU-CROWD++ v2.0'
```

## 한계와 후속 작업

- 팀 검증셋은 11장뿐이므로 실제 제보 환경 전체를 대표하지 않는다.
- SPARSE 표본 한 장에서는 에포크 5도 많은 오탐이 남아 있다.
- 아직 승인하지 않은 팀 사진 84장의 머리 좌표를 검수하면 SAFY 데이터의
  비중과 시나리오 다양성을 늘릴 수 있다.
- 선택 가중치는 Git에 포함되지 않으므로 모델 아티팩트 저장·배포 방식이
  필요하다.
- 운영 적용 전 별도 고정 테스트셋으로 최종 회귀 검증하고 기존 가중치로
  되돌릴 수 있는 롤백 절차를 준비해야 한다.
