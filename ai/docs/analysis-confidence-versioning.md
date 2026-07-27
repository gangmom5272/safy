# 분석 신뢰도와 모델 버전 기록 기준

## 문서 상태

- 관련 이슈: `S15P11A503-78`
- 적용 범위: SAFY MVP
- 모델: `YOLO:yolo11n`
- 가중치 식별자: `yolo11n.pt`
- 혼잡도 규칙: `yolo-grid-v0.2`
- 프레임 품질 규칙: `frame-quality-v0.2`

## 목적

같은 이미지라도 모델 가중치와 분석 규칙이 변경되면 결과가 달라질 수 있다.

분석 결과를 재현하고 변경 전후를 비교할 수 있도록 다음 정보를 함께 기록한다.

- 모델 이름
- 모델 버전
- 가중치 식별자
- 혼잡도 계산 규칙 버전
- 프레임 품질 판정 규칙 버전

또한 개별 사람 탐지 신뢰도와 전체 분석 결과 신뢰도를 서로 다른 값으로 구분한다.

## 신뢰도 필드 구분

### personConfidences

`personConfidences`는 YOLO가 탐지한 각 사람의 탐지 신뢰도 목록이다.

```json
{
  "personCount": 4,
  "personConfidences": [
    0.91,
    0.88,
    0.84,
    0.72
  ]
}
```

목록의 각 값은 탐지된 사람 한 명에 대응한다.

이 값은 개별 객체 탐지 결과의 신뢰도이며 전체 프레임 분석의 정확도를 의미하지 않는다.

`personConfidences`의 평균을 전체 분석 신뢰도로 사용하지 않는다.

고밀도 군중에서는 탐지되지 않은 사람이 목록에 포함되지 않기 때문에 탐지된 사람들의 평균 신뢰도가 높아도 실제 인원수 오차는 클 수 있다.

### confidence

`confidence`는 전체 분석 결과에 대한 보정된 신뢰도를 의미한다.

다음 결과를 종합하고 실제 검증 자료로 보정한 뒤에만 값을 산정해야 한다.

- 프레임 품질
- 사람 탐지 안정성
- 인원수 오차
- 혼잡도 계산 안정성
- 모델 적용 범위
- 실제 SAFY 촬영 환경의 검증 결과

현재는 보정된 계산식과 충분한 현장 표본이 없으므로 임의의 값을 반환하지 않는다.

```json
{
  "confidence": null
}
```

API 스키마는 향후 보정된 값 사용을 위해 `0.0~1.0` 범위의 숫자 또는 `null`을 허용한다.

## 모델 및 규칙 버전 필드

| API 필드 | 현재 값 | 의미 |
|---|---|---|
| `modelName` | `YOLO` | 사용한 모델 계열 |
| `modelVersion` | `yolo11n` | 모델 구조 또는 배포 버전 |
| `modelWeightId` | `yolo11n.pt` | 실제 추론에 사용한 가중치 식별자 |
| `congestionRuleVersion` | `yolo-grid-v0.2` | C·L 계산 규칙 버전 |
| `frameQualityRuleVersion` | `frame-quality-v0.2` | 프레임 품질 판정 규칙 버전 |

가중치 파일 자체는 Git이나 DB에 저장하지 않는다.

`modelWeightId`에는 가중치를 식별할 수 있는 값만 기록한다.

가중치가 변경되면 파일명이 같더라도 기존 결과와 구분할 수 있도록 새로운 식별자를 사용해야 한다.

## 상태별 응답 기준

### COMPLETED

프레임 품질 검사와 모델 추론이 정상적으로 완료된 상태다.

```json
{
  "analysisStatus": "COMPLETED",
  "personCount": 4,
  "personConfidences": [
    0.91,
    0.88,
    0.84,
    0.72
  ],
  "visualCongestionScore": 1.88,
  "localPeakCongestionScore": 15.0,
  "densityLevel": null,
  "riskScore": null,
  "confidence": null,
  "qualityIssues": [],
  "frameWidth": 810,
  "frameHeight": 1080,
  "brightnessScore": 127.5,
  "blurScore": 350.0,
  "modelName": "YOLO",
  "modelVersion": "yolo11n",
  "modelWeightId": "yolo11n.pt",
  "congestionRuleVersion": "yolo-grid-v0.2",
  "frameQualityRuleVersion": "frame-quality-v0.2",
  "processingTimeMs": 36
}
```

### INVALID_FRAME

이미지는 디코딩됐지만 품질 기준을 만족하지 못해 모델 추론을 실행하지 않은 상태다.

```json
{
  "analysisStatus": "INVALID_FRAME",
  "personCount": null,
  "personConfidences": [],
  "visualCongestionScore": null,
  "localPeakCongestionScore": null,
  "densityLevel": null,
  "riskScore": null,
  "confidence": null,
  "qualityIssues": [
    "TOO_DARK"
  ],
  "frameWidth": 1280,
  "frameHeight": 720,
  "brightnessScore": 18.5,
  "blurScore": 120.0,
  "modelName": "YOLO",
  "modelVersion": "yolo11n",
  "modelWeightId": "yolo11n.pt",
  "congestionRuleVersion": "yolo-grid-v0.2",
  "frameQualityRuleVersion": "frame-quality-v0.2",
  "processingTimeMs": 0
}
```

`personCount`가 `0`인 것과 구분하기 위해 분석하지 못한 경우에는 `null`을 사용한다.

### FAILED

프레임 품질 검사는 통과했지만 모델 추론 또는 혼잡도 계산 중 예외가 발생한 상태다.

```json
{
  "analysisStatus": "FAILED",
  "personCount": null,
  "personConfidences": [],
  "visualCongestionScore": null,
  "localPeakCongestionScore": null,
  "densityLevel": null,
  "riskScore": null,
  "confidence": null,
  "qualityIssues": [],
  "frameWidth": 1280,
  "frameHeight": 720,
  "brightnessScore": 127.5,
  "blurScore": 350.0,
  "modelName": "YOLO",
  "modelVersion": "yolo11n",
  "modelWeightId": "yolo11n.pt",
  "congestionRuleVersion": "yolo-grid-v0.2",
  "frameQualityRuleVersion": "frame-quality-v0.2",
  "processingTimeMs": 0
}
```

## DB 기록 기준

### ai_analysis_runs

한 번의 분석 실행에 사용된 실행 환경과 버전을 기록한다.

- `model_name`
- `model_version`
- `model_weight_id`
- `congestion_rule_version`
- `frame_quality_rule_version`
- 분석 상태
- 실패 원인
- 처리시간

### ai_analysis_results

정상적으로 생성된 분석 결과를 기록한다.

- 사람 수
- 전체 시각적 혼잡도 C
- 국소 최대 혼잡도 L
- 보정된 분석 신뢰도

현재 `confidence`는 `NULL`로 저장한다.

`personConfidences`는 개별 탐지 디버그 정보이므로 MVP DB에는 저장하지 않는다.

## 백엔드와 합의할 항목

- AI의 `analysisStatus`와 백엔드 분석 상태 값의 매핑
- `INVALID_FRAME`과 `FAILED`의 실패 원인 저장 위치
- `COMPLETED` 결과만 `ai_analysis_results`에 저장할지 여부
- API camelCase와 DB snake_case의 필드 매핑
- 분석 실행과 결과 테이블의 생성 시점
- 동일 프레임 재분석 시 새로운 실행 레코드를 생성할지 여부
- 모델 가중치 변경 시 `modelWeightId` 작명 규칙
- 향후 `confidence` 계산식과 버전을 어디에 기록할지 여부

## 현재 결정

- `personConfidences`는 개별 YOLO 탐지 신뢰도다.
- 탐지 신뢰도의 단순 평균은 전체 분석 신뢰도로 사용하지 않는다.
- 전체 분석 신뢰도 `confidence`는 현재 `null`을 반환한다.
- API 스키마는 향후 `0.0~1.0`의 보정된 값을 허용한다.
- 모델과 분석 규칙 버전은 모든 분석 상태에 포함한다.
- 가중치 파일 대신 `modelWeightId`만 API와 DB에 기록한다.