# SAFY 머리 좌표 학습 데이터셋

## 문서 상태

- 관련 이슈: `S15P11A503-123`
- 라벨 형식 버전: `head-point-v1`
- 최종 데이터셋 이름: `safy-head-point-v1`
- 상태: 학습 데이터셋 구축 및 검증 완료
- 데이터 저장 위치: `runs/datasets/safy-head-candidates/`

검증용 이미지, 외부 데이터셋, 팀 사진, 머리 좌표 라벨과 생성된 manifest는 용량 및 라이선스 문제로 `runs/` 아래에만 저장하며 Git 저장소에는 포함하지 않는다.

## 목적

P2PNet을 SAFY 환경에 맞게 미세 조정하기 위한 머리 좌표 학습 데이터를 구축한다.

특히 기존 P2PNet이 사람이 없거나 적은 장면에서 배경 패턴을 사람으로 오인하는 문제를 보완하기 위해 다음 데이터를 함께 사용한다.

- JHU-CROWD++ 공식 머리 좌표 데이터
- 사용 동의를 받은 팀 사진의 수동 머리 좌표 라벨
- 사람이 없는 장면
- 사람이 적은 장면
- 국소적으로 사람이 몰린 장면
- 고밀도 군중 장면

## 최종 데이터셋 구성

| 항목 | 값 |
|---|---:|
| 전체 이미지 | 4,395장 |
| 전체 머리 좌표 | 1,497,912개 |
| JHU-CROWD++ | 4,344장 |
| 팀 동의 사진 | 51장 |
| TRAIN | 2,310장 |
| VALIDATION | 496장 |
| TEST | 1,589장 |

시나리오 구성:

| 시나리오 | 이미지 수 |
|---|---:|
| EMPTY | 16 |
| SPARSE | 81 |
| NORMAL | 194 |
| LOCAL_DENSE | 1,004 |
| CROWDED | 3,100 |

최종 manifest:

```text
runs/datasets/safy-head-candidates/ready/safy-head-point-v1/manifest.csv
```

## 머리 좌표 기준

사람 한 명마다 머리 중심에 점 하나를 기록한다.

- 정면, 측면, 뒤통수 모두 얼굴이 아닌 머리 전체의 중심을 사용한다.
- 모자나 헬멧을 쓴 경우 실제 머리가 있을 것으로 보이는 중심을 사용한다.
- 일부가 가려졌더라도 사람과 머리 위치를 확실히 식별할 수 있으면 기록한다.
- 머리 위치를 합리적으로 판단할 수 없으면 해당 사람을 임의로 기록하지 않는다.
- 사진, 포스터, 간판, 조형물 속 인물은 실제 현장 사람이 아니므로 제외한다.
- 같은 사람에게 좌표를 두 번 기록하지 않는다.
- 일부 사람만 기록한 불완전한 이미지는 학습 데이터로 승인하지 않는다.

흔들린 사진도 모든 사람의 머리를 확실히 구분할 수 있으면 사용할 수 있다. 사람 수나 머리 위치가 불확실한 사진은 학습 데이터에서 제외한다.

## 라벨 파일 형식

각 이미지의 머리 좌표는 UTF-8 CSV로 저장한다.

```csv
x,y
100.0,120.0
320.0,240.0
```

사람이 없는 이미지는 헤더만 저장한다.

```csv
x,y
```

좌표 범위:

```text
0 <= x < image_width
0 <= y < image_height
```

## Manifest 형식

표준 manifest는 다음 필드를 사용한다.

| 필드 | 의미 |
|---|---|
| sample_id | 데이터셋 전체에서 고유한 표본 ID |
| source | 데이터 출처 |
| source_group | 유사 이미지와 분할 누수를 방지할 그룹 |
| split | TRAIN, VALIDATION, TEST |
| image_path | manifest 기준 이미지 상대경로 |
| annotation_path | manifest 기준 좌표 CSV 상대경로 |
| image_width | EXIF 방향을 적용한 이미지 너비 |
| image_height | EXIF 방향을 적용한 이미지 높이 |
| person_count | 머리 좌표 개수 |
| scenario | 혼잡 시나리오 |
| license | 사용 조건 |
| consent_confirmed | 팀 사진 사용 동의 여부 |
| image_sha256 | 이미지 무결성 검증값 |
| label_format_version | 머리 좌표 형식 버전 |

## 시나리오 분류

수동 검수된 좌표를 기준으로 시나리오를 다시 계산한다.

```text
0명       → EMPTY
1~5명     → SPARSE
51명 이상 → CROWDED
```

6~50명은 C·L 혼잡도 규칙으로 구분한다.

```text
L >= 37.5 → LOCAL_DENSE
L < 37.5  → NORMAL
```

이미지의 이전 분류와 수동 머리 좌표 수가 다르면 수동 검수 결과를 우선한다.

## JHU-CROWD++ 변환

원본 위치:

```text
runs/datasets/safy-head-candidates/raw/jhu-crowd/extracted/jhu_crowd_v2.0
```

변환 명령:

```bash
python -m scripts.build_jhu_head_point_dataset \
  runs/datasets/safy-head-candidates/raw/jhu-crowd/extracted/jhu_crowd_v2.0 \
  --output runs/datasets/safy-head-candidates/ready/jhu-crowd-v2-calibrated
```

JHU 원본 좌표 중 이미지 경계를 벗어나는 좌표가 발견되어 다음 정책을 적용했다.

- 이미지 크기의 1% 이내로 벗어난 좌표는 가장 가까운 경계 픽셀로 보정
- 1%를 초과한 좌표가 있는 표본은 좌표 신뢰성이 낮다고 보고 표본 전체 제외
- 원본 데이터는 변경하지 않고 변환본에만 정책 적용
- 보정 및 제외 내역은 `conversion-report.csv`에 기록

변환 결과:

| 항목 | 값 |
|---|---:|
| 원본 표본 | 4,372장 |
| 좌표 보정 표본 | 92장 |
| 보정 좌표 | 168개 |
| 제외 표본 | 28장 |
| 최종 표본 | 4,344장 |

검증 명령:

```bash
python -m scripts.validate_head_point_dataset \
  runs/datasets/safy-head-candidates/ready/jhu-crowd-v2-calibrated/manifest.csv
```

JHU-CROWD++ 데이터는 원본 라이선스와 비상업적 사용 조건을 확인해야 하며 이미지와 라벨을 Git 저장소에 재배포하지 않는다.

## 팀 동의 사진 처리

후보 manifest:

```text
runs/datasets/safy-head-candidates/manifests/team-consented-candidates.csv
```

총 135장의 사용 동의 사진을 확보했다.

P2PNet은 저밀도 사진에서 다음과 같은 큰 오탐을 보였다.

- 사람이 없는 사진을 최대 2,692명으로 예측
- SPARSE 사진을 최대 1,277명으로 예측
- 배경의 나무, 벽돌, 글자와 반복 패턴을 머리로 오인

따라서 P2PNet 예측을 정답 라벨로 바로 사용하지 않는다.

초기 라벨 정책:

| 시나리오 | 초기 라벨 방식 |
|---|---|
| EMPTY | 빈 라벨에서 확인 |
| SPARSE | 빈 라벨에서 사람이 직접 좌표 추가 |
| NORMAL | P2PNet 좌표를 참고하되 수동 승인 필요 |
| LOCAL_DENSE | P2PNet 좌표를 참고하되 수동 승인 필요 |
| CROWDED | P2PNet 좌표를 참고하되 수동 승인 필요 |

초기 라벨 생성:

```bash
python -m scripts.generate_team_head_preannotations \
  runs/datasets/safy-head-candidates/manifests/team-consented-candidates.csv \
  --p2pnet-root runs/candidate-models/p2pnet \
  --weight-path runs/candidate-models/p2pnet/weights/SHTechA.pth \
  --output runs/datasets/safy-head-candidates/ready/team-consented-preannotations \
  --device cuda \
  --threshold 0.5
```

EMPTY와 SPARSE 검수:

```bash
python -m scripts.review_team_head_points \
  runs/datasets/safy-head-candidates/ready/team-consented-preannotations/review-manifest.csv \
  --scenarios EMPTY SPARSE
```

검수 도구 조작:

```text
왼쪽 클릭       좌표 추가
오른쪽 클릭     가까운 좌표 삭제
C               좌표 전체 삭제
Z               마지막 변경 취소
A               승인하고 다음 사진으로 이동
S               승인하지 않고 건너뛰기
Q 또는 Esc      종료
```

승인 결과:

| 항목 | 값 |
|---|---:|
| 승인 이미지 | 51장 |
| 수동 검수 머리 좌표 | 144개 |
| EMPTY | 10장 |
| SPARSE | 33장 |
| NORMAL | 5장 |
| LOCAL_DENSE | 3장 |

`PENDING_REVIEW` 상태인 나머지 84장은 최종 학습 manifest에 포함하지 않는다.

승인 데이터 manifest:

```text
runs/datasets/safy-head-candidates/ready/team-consented-preannotations/approved-manifest.csv
```

## EXIF 방향 처리

휴대폰 사진 135장 중 55장은 JPEG 원본 픽셀 방향과 화면에 보이는 방향이 달랐다.

다음 처리 단계에서 모두 `ImageOps.exif_transpose()`를 적용한다.

- P2PNet 입력
- 머리 좌표 검수 화면
- 이미지 크기 검증

manifest의 `image_width`와 `image_height`는 EXIF 방향을 적용한 화면 기준 크기다.

## P2PNet 입력 크기 제한

고해상도 팀 사진의 GPU 메모리 사용량을 제한하기 위해 P2PNet 입력의 긴 변을 최대 1,536픽셀로 제한한다.

P2PNet 결과는 정규화 좌표로 반환한 뒤 원본 이미지 크기의 픽셀 좌표로 복원한다.

## 최종 데이터셋 결합

```bash
python -m scripts.combine_head_point_datasets \
  runs/datasets/safy-head-candidates/ready/jhu-crowd-v2-calibrated/manifest.csv \
  runs/datasets/safy-head-candidates/ready/team-consented-preannotations/approved-manifest.csv \
  --output runs/datasets/safy-head-candidates/ready/safy-head-point-v1/manifest.csv
```

최종 검증:

```bash
python -m scripts.validate_head_point_dataset \
  runs/datasets/safy-head-candidates/ready/safy-head-point-v1/manifest.csv
```

검증 항목:

- `sample_id` 중복
- 이미지 SHA-256
- EXIF 적용 이미지 크기
- 좌표 범위
- 좌표 중복
- `person_count`와 좌표 개수 일치
- 지원하는 split 및 라벨 버전
- 동일 이미지의 분할 누수
- 동일 `source_group`의 분할 누수
- 팀 사진 사용 동의
- 이미지 및 좌표 파일 존재

## 현재 제한 사항

- 팀 데이터는 EMPTY와 SPARSE 중심으로 51장만 승인했다.
- NORMAL, LOCAL_DENSE, CROWDED 팀 사진 84장은 추가 수동 검수가 필요하다.
- JHU 데이터가 전체 좌표 수의 대부분을 차지하므로 팀 데이터의 학습 가중치 또는 샘플링 비율을 별도로 조정해야 한다.
- 이 데이터셋을 사용한 P2PNet 미세 조정과 전후 성능 비교는 후속 작업에서 수행한다.