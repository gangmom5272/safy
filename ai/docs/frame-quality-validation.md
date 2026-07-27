# 프레임 품질 판정 기준 검증 및 보정

## 1. 문서 상태

- 품질 규칙 버전: `frame-quality-v0.2`
- 적용 범위: SAFY AI 프레임 사전 검사
- 관련 이슈: `S15P11A503-77`
- 선행 이슈: `S15P11A503-35`, `S15P11A503-73`
- 상태: 공개 촬영 표본 기반 1차 보정

이 문서는 사람 탐지 전에 실행하는 프레임 품질 검사가 정상 프레임을
과도하게 거부하거나 불량 프레임을 통과시키지 않는지 검증한 결과를
기록한다.

## 2. 검사 위치

품질 검사는 YOLO 사람 탐지 전에 실행한다.

```text
이미지 입력
→ 디코딩
→ 해상도·밝기·블러 검사
→ 유효하지 않으면 INVALID_FRAME 반환
→ 유효하면 YOLO 사람 탐지 진행
```

불량 프레임에서 불필요한 GPU 추론을 실행하지 않고, 분석할 수 없는
입력과 모델 추론 실패를 구분하기 위한 단계다.

## 3. 품질 점수

### 해상도

다음 두 조건 중 하나라도 만족하지 못하면 `LOW_RESOLUTION`으로 판정한다.

```text
짧은 변 >= 240px
전체 픽셀 수 >= 320 × 240
```

### 밝기

이미지를 회색조로 변환한 뒤 전체 픽셀 평균을 밝기 점수로 사용한다.

```text
brightnessScore = grayscale pixel mean
```

판정 기준:

```text
brightnessScore < 35
→ TOO_DARK

brightnessScore > 220
→ TOO_BRIGHT
```

### 블러

회색조 이미지에 라플라시안 연산을 적용하고 분산을 블러 점수로 사용한다.

```text
blurScore = variance(Laplacian(grayImage))
```

판정 기준:

```text
blurScore < 80
→ TOO_BLURRY
```

블러 점수가 낮을수록 경계와 세부 정보가 적은 이미지다.

## 4. v0.1 기준

기존 `frame-quality-v0.1`에 해당하는 기준은 다음과 같았다.

```text
MIN_SHORT_SIDE = 240
MIN_PIXEL_COUNT = 640 × 360
MIN_BRIGHTNESS = 35
MAX_BRIGHTNESS = 220
MIN_BLUR_SCORE = 80
```

밝기와 블러 기준은 정상 표본에서 과거부가 발견되지 않았지만,
전체 픽셀 기준이 정상 프레임을 과도하게 거부하는 문제가 확인됐다.

## 5. 정상 표본 분포

ShanghaiTech Part A·B 테스트 이미지 498장을 이용해 정상 촬영 이미지의
밝기·블러·해상도 분포를 확인했다.

### ShanghaiTech Part A

- 표본 수: 182장
- 밝기 최솟값: 47.16
- 밝기 5백분위: 60.64
- 밝기 중앙값: 96.84
- 밝기 95백분위: 129.84
- 밝기 최댓값: 142.04
- 블러 최솟값: 139.28
- 블러 5백분위: 428.25
- 블러 중앙값: 2,140.78
- 짧은 변 최솟값: 200px

### ShanghaiTech Part B

- 표본 수: 316장
- 밝기 최솟값: 58.64
- 밝기 5백분위: 80.39
- 밝기 중앙값: 109.21
- 밝기 95백분위: 146.24
- 밝기 최댓값: 161.11
- 블러 최솟값: 188.24
- 블러 5백분위: 443.07
- 블러 중앙값: 953.91
- 짧은 변: 768px

정상 표본 498장에서는 밝기와 블러 기준으로 거부된 이미지가 없었다.

따라서 다음 기준은 유지했다.

```text
MIN_BRIGHTNESS = 35
MAX_BRIGHTNESS = 220
MIN_BLUR_SCORE = 80
```

## 6. 해상도 과거부 분석

기존 전체 픽셀 기준 `640×360`을 적용하면 ShanghaiTech Part A 정상 표본
182장 중 26장이 `LOW_RESOLUTION`으로 거부됐다.

```text
기존:
validCount = 156
invalidCount = 26
```

해당 26장의 YOLO 탐지율은 다른 표본보다 특별히 낮지 않았다.

| 그룹 | 표본 수 | 정답 총인원 | YOLO 탐지 | 탐지율 | MAE |
|---|---:|---:|---:|---:|---:|
| 기존 정상 해상도 | 156 | 73,176 | 731 | 1.00% | 464.39 |
| 기존 저해상도 | 26 | 5,686 | 81 | 1.42% | 215.58 |

ShanghaiTech Part A는 초고밀도 데이터이므로 두 그룹 모두 YOLO 탐지율이
낮다. 하지만 기존 저해상도 그룹이 해상도 때문에 특별히 더 분석
불가능하다는 근거는 확인되지 않았다.

## 7. 축소 표본 검증

기존 4명 탐지 표본인 `bus.jpg`를 같은 비율로 단계적으로 축소해
YOLO 사람 수와 confidence를 비교했다.

| 크기 | 사람 수 | confidence | 기존 판정 |
|---|---:|---|---|
| 360×480 | 4 | 0.882, 0.880, 0.857, 0.584 | LOW_RESOLUTION |
| 240×320 | 4 | 0.886, 0.885, 0.854, 0.568 | LOW_RESOLUTION |
| 200×267 | 4 | 0.881, 0.846, 0.845, 0.481 | LOW_RESOLUTION |
| 160×213 | 3 | 0.867, 0.803, 0.795 | LOW_RESOLUTION |
| 120×160 | 4 | 0.893, 0.856, 0.834, 0.328 | LOW_RESOLUTION |

`240×320`까지는 원본과 같은 4명을 유지했고 confidence도 안정적이었다.

`200×267`부터 최저 confidence가 감소했고, `160×213`에서는 한 명을
놓쳤다.

따라서 짧은 변 240px 기준은 유지하고 전체 픽셀 기준만
`320×240`으로 완화했다.

## 8. v0.2 보정

`frame-quality-v0.2` 기준은 다음과 같다.

```text
MIN_SHORT_SIDE = 240
MIN_PIXEL_COUNT = 320 × 240
MIN_BRIGHTNESS = 35
MAX_BRIGHTNESS = 220
MIN_BLUR_SCORE = 80
```

변경 내용:

```text
MIN_PIXEL_COUNT:
640 × 360
→ 320 × 240
```

유지 내용:

- 짧은 변 240px
- 최소 밝기 35
- 최대 밝기 220
- 최소 블러 점수 80
- 품질 이슈 코드
- FastAPI 응답 필드와 타입

보정 후 ShanghaiTech Part A 결과는 다음과 같다.

```text
sampleCount = 182
validCount = 178
invalidCount = 4
LOW_RESOLUTION = 4
```

정상 표본 과거부가 26장에서 4장으로 감소했다.

남은 4장은 짧은 변이 240px 미만이므로 계속 거부한다.

## 9. 대표 품질 시나리오 결과

정상 원본과 동일 원본에서 생성한 품질 변형을 포함해 8개 표본을
검증했다.

| 시나리오 | 크기 | 밝기 | 블러 | 기대 이슈 | 결과 |
|---|---:|---:|---:|---|---|
| NORMAL | 1024×768 | 126.59 | 822.97 | 없음 | 일치 |
| DARK_BOUNDARY | 1024×768 | 58.64 | 274.91 | 없음 | 일치 |
| BLUR_BOUNDARY | 1024×768 | 92.98 | 188.24 | 없음 | 일치 |
| RESOLUTION_BOUNDARY | 320×240 | 126.58 | 2,174.84 | 없음 | 일치 |
| NIGHT | 1024×768 | 18.48 | 26.80 | TOO_DARK, TOO_BLURRY | 일치 |
| TOO_BRIGHT | 1024×768 | 241.69 | 14.67 | TOO_BRIGHT, TOO_BLURRY | 일치 |
| BLURRY | 1024×768 | 126.57 | 2.34 | TOO_BLURRY | 일치 |
| LOW_RESOLUTION | 267×200 | 126.58 | 2,402.90 | LOW_RESOLUTION | 일치 |

평가 결과:

```text
sampleCount = 8
matchedCount = 8
mismatchedCount = 0
```

## 10. 복합 품질 이슈

야간 표본은 `TOO_DARK`와 `TOO_BLURRY`가 함께 발생했다.

과도하게 밝은 표본은 `TOO_BRIGHT`와 `TOO_BLURRY`가 함께 발생했다.

밝기 부족이나 과노출로 이미지 대비가 사라지면 라플라시안 분산도
낮아질 수 있다.

품질 검사는 첫 번째 문제만 반환하지 않고 발견한 모든 이슈를 반환한다.

## 11. 평가 실행 방법

모든 명령은 `ai` 디렉터리에서 가상환경을 활성화한 상태로 실행한다.

```bash
python -m scripts.evaluate_frame_quality \
  docs/frame-quality-manifest.example.csv \
  --output runs/evaluation/frame-quality-report.json
```

평가 매니페스트 필드:

```text
sample_id
scenario
image_path
expected_issues
```

여러 기대 이슈는 `|`로 구분한다.

```text
TOO_DARK|TOO_BLURRY
```

평가 결과 JSON에는 다음 정보가 포함된다.

- 기대 유효 상태
- 실제 유효 상태
- 기대 품질 이슈
- 실제 품질 이슈
- 기대 결과 일치 여부
- 이미지 너비와 높이
- 밝기 점수
- 블러 점수
- 전체 일치·불일치 건수

실제 이미지와 결과 JSON은 `runs/`에 저장하며 Git에 포함하지 않는다.

## 12. API 상태 회귀 기준

### 정상 프레임

```text
analysisStatus = COMPLETED
```

### 품질 기준을 통과하지 못한 프레임

```text
analysisStatus = INVALID_FRAME
```

### 디코딩할 수 없는 이미지

```text
analysisStatus = INVALID_FRAME
qualityIssues = ["DECODE_FAILED"]
```

### 품질 검사 또는 모델 추론 중 예외

```text
analysisStatus = FAILED
```

이번 보정에서는 기존 상태 코드, 응답 필드명과 타입을 변경하지 않는다.

## 13. 테스트 범위

다음 상황을 자동 테스트한다.

- 디코딩할 수 없는 바이트
- 기존 저해상도 프레임
- 320×240 최소 해상도 경계
- 어두운 프레임
- 과도하게 밝은 프레임
- 블러 프레임
- 정상 프레임
- 품질 규칙 버전
- 밝음·어두움·블러·저해상도 변형 생성
- 매니페스트 로드
- 중복 `sample_id` 거부
- 기대 결과와 실제 결과 비교
- JSON 요약 및 디버그 점수 저장
- 평가 실행 스크립트

## 14. 한계

- ShanghaiTech는 실제 SAFY 시민 제보 데이터가 아니다.
- NIGHT, TOO_BRIGHT, BLURRY, LOW_RESOLUTION은 동일 원본의 합성 변형이다.
- 라플라시안 분산은 이미지 크기와 장면의 질감에 영향을 받는다.
- 실제 야간 촬영의 노이즈와 흔들림을 완전히 재현하지 못한다.
- 현재 기준은 얼굴이나 번호판 식별 가능 여부를 판단하지 않는다.
- 정상 표본 중 짧은 변 240px 미만인 4장은 계속 거부된다.

## 15. 재검토 조건

다음 데이터가 확보되면 임계값을 다시 검토한다.

- 실제 SAFY 시민 제보 정상 표본
- 실제 야간·역광·과노출 표본
- 흔들림과 초점 불량이 포함된 실제 촬영 표본
- 다양한 스마트폰 해상도와 압축률 표본
- 정상인데 `INVALID_FRAME`으로 거부된 운영 사례
- 불량인데 `COMPLETED`로 통과한 운영 사례

개인정보가 포함된 실제 시민 제보 원본은 Git에 포함하지 않는다.