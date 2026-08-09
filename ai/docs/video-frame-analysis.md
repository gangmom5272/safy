# 영상 프레임 분석 상태 및 실패 처리 기준

## 문서 상태

- 관련 이슈: `S15P11A503-97`
- 적용 범위: SAFY MVP
- 분석 단위: 영상에서 5초 간격으로 추출한 단일 프레임
- 프레임 추출 담당: OpenVidu 및 미디어 처리 영역
- 프레임 분석 담당: AI 서버
- 재시도 및 영상 단위 집계 담당: 백엔드

## 전체 처리 흐름

```text
제보 영상 수신
→ OpenVidu 측에서 5초 간격으로 프레임 추출
→ 추출한 프레임을 AI 단일 프레임 분석 API로 전송
→ AI가 품질 검사, 사람 탐지, C·L 계산 수행
→ AI가 프레임 분석 결과 JSON 반환
→ 백엔드가 프레임별 결과와 시도 횟수 저장
→ 백엔드가 제보 영상 단위 결과와 최종 위험도 계산
```

AI 서버는 영상을 직접 분할하지 않는다. 이미 추출된 이미지 한 장을 입력받아 분석한다.

현재 분석 API는 다음과 같다.

```text
POST /api/v1/analyze/frame
```

## 프레임 분석 상태

### COMPLETED

프레임 품질 검사, 사람 탐지 및 C·L 계산을 모두 정상적으로 완료한 상태다.

주요 결과:

```text
analysisStatus = COMPLETED
personCount = 탐지된 사람 수
visualCongestionScore = 전체 시각적 혼잡도 C
localPeakCongestionScore = 국소 최대 혼잡도 L
qualityIssues = []
failureCodes = []
```

정상 프레임에서 사람이 탐지되지 않은 경우에는 `personCount = 0`으로 반환한다.

### INVALID_FRAME

이미지를 읽을 수 없거나 품질 기준을 통과하지 못해 분석 대상에서 제외한 상태다.

```text
analysisStatus = INVALID_FRAME
personCount = null
visualCongestionScore = null
localPeakCongestionScore = null
qualityIssues = 품질 문제 코드 배열
failureCodes = []
```

`INVALID_FRAME`은 사람 수가 0명인 프레임이 아니다. 영상 단위 사람 수, 혼잡도 및 위험도 계산에서 제외한다.

품질 문제 코드는 다음 값만 사용한다.

```text
DECODE_FAILED
LOW_RESOLUTION
TOO_DARK
TOO_BRIGHT
TOO_BLURRY
```

`INVALID_FRAME`은 동일한 프레임을 다시 분석해도 결과가 달라질 가능성이 낮으므로 재시도하지 않는다.

### FAILED

품질 검사를 통과한 뒤 AI 내부 처리 중 시스템 오류가 발생한 상태다.

```text
analysisStatus = FAILED
personCount = null
visualCongestionScore = null
localPeakCongestionScore = null
qualityIssues = []
failureCodes = 실패 코드 배열
```

AI 서버가 반환하는 실패 코드는 다음 값만 사용한다.

```text
MODEL_INFERENCE_FAILED
CONGESTION_CALCULATION_FAILED
INTERNAL_AI_ERROR
```

`FAILED`도 사람 수가 0명인 프레임이 아니며 영상 단위 계산에서 제외한다.

백엔드는 `FAILED` 프레임을 한 번 재시도한다. 재시도까지 실패하면 실패 결과를 저장하고 다음 5초 프레임 분석을 계속한다.

## 백엔드에서 추가하는 실패 코드

다음 코드는 AI 서버가 정상적인 JSON 응답을 반환하지 못한 상황을 백엔드가 기록할 때 사용한다.

```text
AI_CONNECTION_FAILED
AI_TIMEOUT
INVALID_AI_RESPONSE
```

AI 서버 내부 실패 코드와 백엔드 통신 실패 코드를 하나의 `failureCodes` 배열 형식으로 관리한다.

## 상태별 배열 규칙

응답 데이터의 의미가 섞이지 않도록 다음 규칙을 적용한다.

| 상태 | qualityIssues | failureCodes |
|---|---|---|
| `COMPLETED` | 빈 배열 | 빈 배열 |
| `INVALID_FRAME` | 한 개 이상의 품질 코드 | 빈 배열 |
| `FAILED` | 빈 배열 | 한 개 이상의 실패 코드 |

스키마 검증을 통해 다음 잘못된 응답을 거부한다.

- `COMPLETED` 응답에 `failureCodes`가 포함된 경우
- `INVALID_FRAME` 응답에 `failureCodes`가 포함된 경우
- `INVALID_FRAME` 응답의 `qualityIssues`가 비어 있는 경우
- `FAILED` 응답의 `failureCodes`가 비어 있는 경우
- 정의되지 않은 품질 코드 또는 실패 코드가 포함된 경우

## 예외 정보 처리

실제 Python 예외 메시지와 스택 트레이스는 AI 서버 로그에만 기록한다.

API 응답에는 내부 파일 경로, 모델 구현 정보, 예외 메시지 또는 스택 트레이스를 포함하지 않는다. 호출자는 `analysisStatus`와 표준화된 `failureCodes`만 사용해 실패 원인을 판단한다.

## 프레임 식별과 저장

AI API는 입력된 이미지 한 장을 분석하고 결과 JSON을 반환한다.

다음 정보는 호출자인 백엔드가 관리한다.

- 어떤 제보 영상에서 추출된 프레임인지
- 영상에서 몇 초 지점의 프레임인지
- 동일한 프레임의 몇 번째 분석 시도인지
- AI 응답을 어떤 프레임 분석 실행 기록에 저장할지

백엔드는 `extractedFrameId`와 `attemptNo`를 기준으로 프레임 분석 시도를 구분한다. AI 서버는 백엔드가 관리하는 업무 식별 관계를 임의로 변경하지 않는다.

## 영상 단위 처리

영상 단위 상태와 최종 위험도는 백엔드에서 계산한다.

백엔드는 프레임별 결과를 이용해 다음 개수를 관리한다.

```text
totalFrameCount
processedFrameCount
completedFrameCount
invalidFrameCount
failedFrameCount
```

영상 단위 사람 수, 혼잡도 및 위험도 계산에는 `COMPLETED` 프레임만 사용한다. `INVALID_FRAME`과 최종 재시도까지 실패한 `FAILED` 프레임은 0명으로 치환하지 않고 계산에서 제외한다.

정상 프레임이 부족한 영상의 처리 기준과 최종 위험도 계산식은 실제 제보 영상 테스트 후 백엔드 및 프론트엔드 담당자와 다시 합의한다.

## 분석 메타데이터

각 AI 분석 결과에는 재현과 추적을 위한 다음 정보를 포함한다.

```text
modelName
modelVersion
modelWeightId
congestionRuleVersion
frameQualityRuleVersion
processingTimeMs
```

이 값으로 어떤 모델 가중치와 계산 규칙이 사용됐는지 확인할 수 있다.

## 현재 결정

- 영상 프레임은 OpenVidu 측에서 5초 간격으로 추출한다.
- AI 서버는 추출된 단일 프레임만 분석한다.
- AI는 분석 가능한 값을 JSON으로 모두 반환한다.
- 백엔드는 필요한 값을 DTO와 DB 구조에 맞게 저장한다.
- 개인정보가 포함될 수 있는 원본 프레임은 이 작업에서 Git에 저장하지 않는다.
- 최종 위험도는 백엔드에서 계산한다.
- 프레임 재시도와 영상 단위 집계도 백엔드가 담당한다.